const User = require('../models/User');
const EmergencyRequest = require('../models/EmergencyRequest');
const EmergencyResponse = require('../models/EmergencyResponse');
const ChatMessage = require('../models/ChatMessage');

/**
 * Handles cascading actions when a user account is suspended, blocked, or deleted.
 * 
 * @param {string|Object} userId - The user ID being modified
 * @param {string} newStatus - 'SUSPENDED', 'BLOCKED', 'DELETED', or 'ACTIVE'
 * @param {string} reason - Administrative or user-stated reason
 * @param {Object} actor - The user performing the action
 */
async function handleUserLifecycleChange(userId, newStatus, reason = '', actor = null) {
  const user = await User.findById(userId);
  if (!user) return null;

  const previousStatus = user.accountStatus;
  const isDeactivating = ['SUSPENDED', 'BLOCKED', 'DELETED'].includes(newStatus);

  if (isDeactivating) {
    // 1. Invalidate availability and FCM tokens
    user.isAvailable = false;
    if (newStatus === 'DELETED') {
      user.accountStatus = 'BLOCKED';
      user.isDeleted = true;
      user.fcmToken = null;
      user.fcmTokens = [];
    } else {
      user.accountStatus = newStatus;
    }
    await user.save();

    // 2. Cascade: User as Requester (All open active emergencies created by this user)
    const activeEmergencies = await EmergencyRequest.find({
      $or: [{ requester: user._id }, { requesterId: user._id }],
      status: { $nin: ['COMPLETED', 'CANCELLED', 'EXPIRED', 'FULFILLED', 'DONATION_COMPLETED'] }
    });

    for (const emergency of activeEmergencies) {
      emergency.status = 'CANCELLED';
      emergency.cancelledAt = new Date();
      emergency.cancelledByRole = actor && actor.role === 'ADMIN' ? 'ADMIN' : 'SYSTEM';
      emergency.cancelReason = `Requester account ${newStatus.toLowerCase()} (${reason || 'System Lifecycle Action'})`;
      await emergency.save();

      // Cancel all active donor responses attached to this emergency
      const responses = await EmergencyResponse.find({
        $or: [{ requestId: emergency._id }, { emergencyRequestId: emergency._id }],
        status: { $in: ['ACCEPTED', 'TRAVELLING', 'NOTIFIED'] }
      });

      for (const resp of responses) {
        resp.status = 'CANCELLED';
        resp.cancelledAt = new Date();
        resp.cancellationReason = `Emergency revoked: Requester account ${newStatus.toLowerCase()}`;
        await resp.save();

        // Restore donor availability
        await User.findByIdAndUpdate(resp.donorId, { isAvailable: true });
      }

      // Broadcast termination event into coordination chat
      await ChatMessage.create({
        emergencyRequestId: emergency._id,
        senderId: actor ? actor._id : user._id,
        senderName: 'LifeShare Administration',
        senderRole: 'ADMIN',
        messageType: 'STATUS_CHANGE',
        messageText: `⚠️ Emergency coordination closed: Requester account ${newStatus.toLowerCase()}. Active donor dispatches cancelled.`
      });
    }

    // 3. Cascade: User as Responding Donor (All active responses where this user was a donor)
    const activeDonorResponses = await EmergencyResponse.find({
      donorId: user._id,
      status: { $in: ['ACCEPTED', 'TRAVELLING'] }
    });

    for (const resp of activeDonorResponses) {
      resp.status = 'CANCELLED';
      resp.cancelledAt = new Date();
      resp.cancellationReason = `Donor account ${newStatus.toLowerCase()}`;
      await resp.save();

      // Look up parent emergency request
      const parentId = resp.requestId || resp.emergencyRequestId;
      const parentEmergency = await EmergencyRequest.findById(parentId);
      if (parentEmergency && !['COMPLETED', 'CANCELLED', 'EXPIRED', 'FULFILLED'].includes(parentEmergency.status)) {
        // Broadcast in coordination chat so requester and hospital coordinator know
        await ChatMessage.create({
          emergencyRequestId: parentEmergency._id,
          senderId: user._id,
          senderName: user.name || 'Volunteer Donor',
          senderRole: 'ADMIN',
          messageType: 'STATUS_CHANGE',
          messageText: `⚠️ Responding donor (${user.name || 'Volunteer'}) is no longer available (Account ${newStatus.toLowerCase()}). Search continues for remaining units.`
        });
      }
    }
  } else if (newStatus === 'ACTIVE') {
    user.accountStatus = 'ACTIVE';
    user.isDeleted = false;
    await user.save();
  }

  return {
    userId: user._id,
    previousStatus,
    newStatus,
    reason
  };
}

module.exports = {
  handleUserLifecycleChange
};
