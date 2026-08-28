const express = require('express');
const router = express.Router();
const { authenticateToken } = require('../middleware/auth');
const ChatMessage = require('../models/ChatMessage');
const EmergencyRequest = require('../models/EmergencyRequest');
const EmergencyResponse = require('../models/EmergencyResponse');
const User = require('../models/User');
const Hospital = require('../models/Hospital');

// Helper: Calculate Great Circle Distance in KM
function calculateDistanceKm(lat1, lon1, lat2, lon2) {
  if (!lat1 || !lon1 || !lat2 || !lon2) return null;
  const R = 6371; // Earth radius in km
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.round((R * c) * 10) / 10;
}

// Check if user is authorized to participate in emergency chat
async function authorizeChatParticipant(emergency, userId, userRole) {
  if (userRole === 'ADMIN') return true;

  // 1. Check if user is the requester
  if (emergency.requester && emergency.requester.toString() === userId.toString()) {
    return 'REQUESTER';
  }

  // Check email match if legacy postedBy
  const user = await User.findById(userId).lean();
  if (user && emergency.postedBy && emergency.postedBy.toLowerCase() === user.email.toLowerCase()) {
    return 'REQUESTER';
  }

  // 2. Check if user is an accepted/responding donor
  const donorResponse = await EmergencyResponse.findOne({
    requestId: emergency._id,
    donorId: userId
  }).lean();
  if (donorResponse) {
    return 'DONOR';
  }

  // 3. Check if user is the hospital coordinator for this hospital
  if (user && user.role === 'COORDINATOR' && emergency.hospitalId && user.hospitalId) {
    if (emergency.hospitalId.toString() === user.hospitalId.toString()) {
      return 'COORDINATOR';
    }
  }

  // Allow registered donors to participate in active emergency coordination
  if (user && user.role === 'DONOR') {
    return 'DONOR';
  }

  return false;
}

// GET /api/chat/:emergencyId/messages
router.get('/:emergencyId/messages', authenticateToken, async (req, res) => {
  try {
    const { emergencyId } = req.params;
    const emergency = await EmergencyRequest.findById(emergencyId).lean();
    if (!emergency) {
      return res.status(404).json({ success: false, message: 'Emergency request not found' });
    }

    const messages = await ChatMessage.find({ emergencyRequestId: emergencyId })
      .sort({ createdAt: 1 })
      .limit(150)
      .lean();

    const formattedMessages = messages.map(m => ({
      id: m._id,
      emergencyRequestId: m.emergencyRequestId,
      senderId: m.senderId,
      senderName: m.senderName,
      senderRole: m.senderRole,
      messageType: m.messageType,
      messageText: m.messageText,
      etaMinutes: m.etaMinutes,
      distanceKm: m.distanceKm,
      isSelf: m.senderId.toString() === req.user.id.toString(),
      createdAt: m.createdAt
    }));

    return res.json({
      success: true,
      emergency: {
        id: emergency._id,
        patientName: emergency.patientName,
        bloodGroup: emergency.bloodGroup,
        hospital: emergency.hospital,
        hospitalAddress: emergency.hospitalAddress || '',
        hospitalCoordinates: emergency.hospitalLocation && emergency.hospitalLocation.coordinates ? {
          longitude: emergency.hospitalLocation.coordinates[0],
          latitude: emergency.hospitalLocation.coordinates[1]
        } : null,
        status: emergency.status,
        unitsNeeded: emergency.unitsNeeded || emergency.unitsRequired || 1
      },
      messages: formattedMessages
    });
  } catch (error) {
    console.error('Error fetching chat messages:', error);
    return res.status(500).json({ success: false, message: error.message });
  }
});

// POST /api/chat/:emergencyId/messages
router.post('/:emergencyId/messages', authenticateToken, async (req, res) => {
  try {
    const { emergencyId } = req.params;
    const { messageText, messageType } = req.body;

    if (!messageText || !messageText.trim()) {
      return res.status(400).json({ success: false, message: 'Message text is required' });
    }

    const emergency = await EmergencyRequest.findById(emergencyId);
    if (!emergency) {
      return res.status(404).json({ success: false, message: 'Emergency request not found' });
    }

    const sender = await User.findById(req.user.id).lean();
    if (!sender) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    const senderRole = sender.role || 'DONOR';
    const senderName = sender.name || `${sender.firstName || ''} ${sender.lastName || ''}`.trim() || 'Volunteer';

    const newMsg = await ChatMessage.create({
      emergencyRequestId: emergencyId,
      senderId: req.user.id,
      senderName,
      senderRole,
      messageType: messageType || 'TEXT',
      messageText: messageText.trim()
    });

    return res.status(201).json({
      success: true,
      message: {
        id: newMsg._id,
        emergencyRequestId: newMsg.emergencyRequestId,
        senderId: newMsg.senderId,
        senderName: newMsg.senderName,
        senderRole: newMsg.senderRole,
        messageType: newMsg.messageType,
        messageText: newMsg.messageText,
        isSelf: true,
        createdAt: newMsg.createdAt
      }
    });
  } catch (error) {
    console.error('Error sending chat message:', error);
    return res.status(500).json({ success: false, message: error.message });
  }
});

// POST /api/chat/:emergencyId/eta
router.post('/:emergencyId/eta', authenticateToken, async (req, res) => {
  try {
    const { emergencyId } = req.params;
    const { etaMinutes, latitude, longitude, customStatus } = req.body;

    const emergency = await EmergencyRequest.findById(emergencyId);
    if (!emergency) {
      return res.status(404).json({ success: false, message: 'Emergency request not found' });
    }

    const sender = await User.findById(req.user.id).lean();
    const senderName = sender ? (sender.name || `${sender.firstName || ''} ${sender.lastName || ''}`.trim() || 'Donor') : 'Donor';

    let distanceKm = null;
    if (latitude && longitude && emergency.hospitalLocation && emergency.hospitalLocation.coordinates) {
      const hospLon = emergency.hospitalLocation.coordinates[0];
      const hospLat = emergency.hospitalLocation.coordinates[1];
      distanceKm = calculateDistanceKm(latitude, longitude, hospLat, hospLon);
    }

    let messageText = '';
    if (customStatus) {
      messageText = `${senderName}: ${customStatus}`;
    } else if (etaMinutes) {
      messageText = `On the way - Estimated arrival in ~${etaMinutes} mins${distanceKm ? ` (${distanceKm} km away)` : ''}`;
    } else {
      messageText = `${senderName} updated travel status to In Transit`;
    }

    const newMsg = await ChatMessage.create({
      emergencyRequestId: emergencyId,
      senderId: req.user.id,
      senderName,
      senderRole: sender ? sender.role : 'DONOR',
      messageType: 'ETA_UPDATE',
      messageText,
      etaMinutes: etaMinutes || null,
      distanceKm: distanceKm || null,
      donorCoordinates: (latitude && longitude) ? [longitude, latitude] : undefined
    });

    // Also update donor response status to TRAVELLING if applicable
    await EmergencyResponse.findOneAndUpdate(
      { requestId: emergencyId, donorId: req.user.id },
      { $set: { status: 'TRAVELLING', travellingAt: new Date() } }
    );

    return res.status(201).json({
      success: true,
      message: {
        id: newMsg._id,
        emergencyRequestId: newMsg.emergencyRequestId,
        senderId: newMsg.senderId,
        senderName: newMsg.senderName,
        senderRole: newMsg.senderRole,
        messageType: newMsg.messageType,
        messageText: newMsg.messageText,
        etaMinutes: newMsg.etaMinutes,
        distanceKm: newMsg.distanceKm,
        isSelf: true,
        createdAt: newMsg.createdAt
      }
    });
  } catch (error) {
    console.error('Error posting ETA update:', error);
    return res.status(500).json({ success: false, message: error.message });
  }
});

module.exports = router;
