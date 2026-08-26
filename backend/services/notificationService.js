const fs = require('fs');
const path = require('path');
const DeviceToken = require('../models/DeviceToken');

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
 * Send notification to all devices registered to a specific user
 */
async function sendToUser(userId, { title, body, data = {}, notificationType = 'GENERAL' }) {
  try {
    const tokens = await DeviceToken.find({ userId });
    if (!tokens || tokens.length === 0) {
      return { success: false, reason: 'No registered device tokens for user' };
    }

    const payload = {
      notification: {
        title,
        body
      },
      data: {
        ...data,
        notificationType,
        timestamp: String(Date.now())
      }
    };

    console.log(`🔔 [PUSH NOTIFICATION DISPATCH] User: ${userId} | Type: ${notificationType} | Title: "${title}"`);

    if (admin) {
      const registrationTokens = Array.from(new Set(tokens.map((t) => t.token))).filter(Boolean);
      if (registrationTokens.length === 0) {
        return { success: false, reason: 'No valid registration tokens' };
      }
      const response = await admin.messaging().sendEachForMulticast({
        tokens: registrationTokens,
        ...payload
      });

      // Cleanup invalid tokens
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
      return { success: true, response };
    }

    return { success: true, simulated: true };
  } catch (err) {
    console.error('❌ Error sending push notification:', err.message);
    return { success: false, error: err.message };
  }
}

/**
 * Notify nearby matching donors about an urgent SOS
 */
async function sendEmergencyNotification(userId, emergency) {
  return sendToUser(userId, {
    title: `🚨 Urgent ${emergency.bloodGroup} Blood Needed!`,
    body: `An emergency blood request for ${emergency.unitsRequired || emergency.unitsNeeded || 1} unit(s) is needed near you at ${emergency.hospital}.`,
    data: {
      requestId: String(emergency._id),
      bloodGroup: emergency.bloodGroup,
      hospital: emergency.hospital,
      urgency: emergency.urgency || 'URGENT',
      units: String(emergency.unitsRequired || emergency.unitsNeeded || 1)
    },
    notificationType: 'EMERGENCY_REQUEST'
  });
}

/**
 * Notify requester when a donor accepts their SOS
 */
async function notifyRequesterOfAcceptance(requesterId, emergency, donor) {
  return sendToUser(requesterId, {
    title: '🩸 Donor Accepted Your Blood Request!',
    body: `A voluntary donor (${donor.bloodGroup}) has accepted your emergency request for ${emergency.hospital}.`,
    data: {
      requestId: String(emergency._id),
      bloodGroup: emergency.bloodGroup,
      donorName: donor.name || 'Voluntary Donor',
      status: 'DONOR_ACCEPTED'
    },
    notificationType: 'DONOR_RESPONSE'
  });
}

/**
 * Notify assigned donors if the emergency is cancelled
 */
async function notifyEmergencyCancelled(userId, emergency) {
  return sendToUser(userId, {
    title: 'Emergency Request Cancelled',
    body: `The blood request for ${emergency.hospital} has been cancelled or resolved.`,
    data: {
      requestId: String(emergency._id),
      status: 'CANCELLED'
    },
    notificationType: 'EMERGENCY_CANCELLED'
  });
}

module.exports = {
  sendToUser,
  sendNotificationToUser: sendToUser,
  sendEmergencyNotification,
  notifyRequesterOfAcceptance,
  notifyEmergencyCancelled
};
