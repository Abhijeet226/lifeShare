const fs = require('fs');
const path = require('path');
const DeviceToken = require('../models/DeviceToken');
const Notification = require('../models/Notification');

// Initialize Firebase Admin SDK for Live FCM Push Notifications
let admin = null;
try {
  let serviceAccount = null;

  if (process.env.FIREBASE_SERVICE_ACCOUNT_PATH) {
    const keyPath = path.resolve(__dirname, '..', process.env.FIREBASE_SERVICE_ACCOUNT_PATH);
    if (fs.existsSync(keyPath)) {
      serviceAccount = JSON.parse(fs.readFileSync(keyPath, 'utf8'));
    }
  } else if (process.env.FIREBASE_SERVICE_ACCOUNT) {
    serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
  }

  if (serviceAccount) {
    const adminSDK = require('firebase-admin');
    adminSDK.initializeApp({
      credential: adminSDK.credential.cert(serviceAccount)
    });
    admin = adminSDK;
    console.log('✅ Firebase Cloud Messaging (FCM Admin SDK) initialized successfully.');
  } else {
    console.log('ℹ️ Firebase Admin SDK not configured (running in simulated push mode).');
  }
} catch (e) {
  console.warn('⚠️ Firebase Admin SDK initialization error:', e.message);
}

/**
 * Determine notification channel based on type
 */
function getChannelForType(type) {
  switch (type) {
    case 'EMERGENCY_REQUEST':
    case 'DONOR_ACCEPTED':
    case 'DONOR_TRAVELLING':
    case 'DONOR_ARRIVED':
    case 'EMERGENCY_RESOLVED':
    case 'EMERGENCY_CANCELLED':
      return 'EMERGENCY';
    case 'CHAT_MESSAGE':
      return 'CHAT';
    case 'DONATION_VERIFIED':
    case 'COOLDOWN_EXPIRED':
      return 'CERTIFICATES';
    default:
      return 'UPDATES';
  }
}

/**
 * Send notification to a specific user, persisting it in MongoDB & dispatching FCM
 */
async function sendToUser(userId, { title, body, data = {}, notificationType = 'GENERAL', collapseKey = null }) {
  try {
    const channel = getChannelForType(notificationType);
    const resolvedCollapseKey = collapseKey || (data.requestId || data.emergencyId ? `emergency_${data.requestId || data.emergencyId}` : null);

    // 1. Persistent In-App Notification (In-Place Upsert if collapseKey exists)
    try {
      if (resolvedCollapseKey) {
        await Notification.findOneAndUpdate(
          { userId, collapseKey: resolvedCollapseKey },
          {
            title,
            body,
            type: notificationType,
            channel,
            collapseKey: resolvedCollapseKey,
            status: data.status || 'ACTIVE',
            data,
            isRead: false,
            isDeleted: false,
            updatedAt: new Date()
          },
          { upsert: true, new: true, setDefaultsOnInsert: true }
        );
      } else {
        await Notification.create({
          userId,
          title,
          body,
          type: notificationType,
          channel,
          collapseKey: null,
          status: data.status || 'ACTIVE',
          data,
          isRead: false,
          isDeleted: false
        });
      }
    } catch (dbErr) {
      console.warn('⚠️ Failed to persist in-app notification to MongoDB:', dbErr.message);
    }

    console.log(`🔔 [NOTIFICATION DISPATCH] User: ${userId} | Channel: ${channel} | Type: ${notificationType} | Title: "${title}"`);

    // 2. FCM Push Notification
    const tokens = await DeviceToken.find({ userId });
    if (!tokens || tokens.length === 0) {
      return { success: true, persisted: true, pushDispatched: false };
    }

    const payload = {
      notification: {
        title,
        body
      },
      data: {
        ...data,
        notificationType,
        channel,
        timestamp: String(Date.now())
      }
    };

    if (admin) {
      const registrationTokens = Array.from(new Set(tokens.map((t) => t.token))).filter(Boolean);
      if (registrationTokens.length > 0) {
        const response = await admin.messaging().sendEachForMulticast({
          tokens: registrationTokens,
          ...payload
        });

        if (response.failureCount > 0) {
          response.responses.forEach((resp, idx) => {
            if (!resp.success) {
              const errCode = resp.error ? resp.error.code : '';
              if (
                errCode === 'messaging/invalid-registration-token' ||
                errCode === 'messaging/registration-token-not-registered'
              ) {
                DeviceToken.deleteOne({ token: registrationTokens[idx] }).exec();
              }
            }
          });
        }
        return { success: true, persisted: true, pushDispatched: true, response };
      }
    }

    return { success: true, persisted: true, pushDispatched: false, simulated: true };
  } catch (err) {
    console.error('❌ Error in sendToUser notification:', err.message);
    return { success: false, error: err.message };
  }
}

/**
 * Notify nearby matching donors about an urgent SOS
 */
async function sendEmergencyNotification(userId, emergency) {
  return sendToUser(userId, {
    title: `🚨 Urgent ${emergency.bloodGroup} Blood Needed!`,
    body: `Emergency request for ${emergency.unitsRequired || emergency.unitsNeeded || 1} unit(s) at ${emergency.hospital}.`,
    data: {
      requestId: String(emergency._id),
      bloodGroup: emergency.bloodGroup,
      hospital: emergency.hospital,
      urgency: emergency.urgency || 'URGENT',
      units: String(emergency.unitsRequired || emergency.unitsNeeded || 1),
      status: 'URGENT'
    },
    notificationType: 'EMERGENCY_REQUEST',
    collapseKey: `emergency_${emergency._id}`
  });
}

/**
 * Notify requester when a donor accepts their SOS
 */
async function notifyRequesterOfAcceptance(requesterId, emergency, donor) {
  return sendToUser(requesterId, {
    title: '🩸 Donor Accepted Your Blood Request!',
    body: `${donor.name || 'A voluntary donor'} (${donor.bloodGroup}) is on the way to ${emergency.hospital}.`,
    data: {
      requestId: String(emergency._id),
      bloodGroup: emergency.bloodGroup,
      donorName: donor.name || 'Voluntary Donor',
      status: 'DONOR_ACCEPTED'
    },
    notificationType: 'DONOR_ACCEPTED',
    collapseKey: `emergency_${emergency._id}`
  });
}

/**
 * Notify assigned donors if the emergency is cancelled or resolved
 */
async function notifyEmergencyCancelled(userId, emergency, reason = 'Resolved') {
  return sendToUser(userId, {
    title: '✅ Emergency SOS Resolved',
    body: `The blood request for ${emergency.hospital} has been resolved (${reason}). Thank you!`,
    data: {
      requestId: String(emergency._id),
      status: 'RESOLVED'
    },
    notificationType: 'EMERGENCY_RESOLVED',
    collapseKey: `emergency_${emergency._id}`
  });
}

/**
 * Clean up passive donor notifications when an emergency is deleted or marked fraud
 */
async function purgeEmergencyNotifications(requestId) {
  try {
    await Notification.deleteMany({
      collapseKey: `emergency_${requestId}`
    });
  } catch (err) {
    console.warn('⚠️ Failed to purge emergency notifications:', err.message);
  }
}

module.exports = {
  sendToUser,
  sendNotificationToUser: sendToUser,
  sendEmergencyNotification,
  notifyRequesterOfAcceptance,
  notifyEmergencyCancelled,
  purgeEmergencyNotifications
};

