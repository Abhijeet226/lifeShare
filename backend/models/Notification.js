const mongoose = require('mongoose');

const notificationSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true,
      index: true
    },
    title: {
      type: String,
      required: true
    },
    body: {
      type: String,
      required: true
    },
    type: {
      type: String,
      enum: [
        'EMERGENCY_REQUEST',
        'DONOR_ACCEPTED',
        'DONOR_TRAVELLING',
        'DONOR_ARRIVED',
        'EMERGENCY_RESOLVED',
        'EMERGENCY_CANCELLED',
        'CHAT_MESSAGE',
        'DONATION_VERIFIED',
        'COOLDOWN_EXPIRED',
        'SYSTEM'
      ],
      default: 'SYSTEM',
      index: true
    },
    channel: {
      type: String,
      enum: ['EMERGENCY', 'CHAT', 'CERTIFICATES', 'UPDATES'],
      default: 'UPDATES',
      index: true
    },
    collapseKey: {
      type: String,
      index: true,
      default: null
    },
    status: {
      type: String,
      default: 'ACTIVE'
    },
    data: {
      requestId: String,
      emergencyId: String,
      chatRoomId: String,
      certificateId: String,
      donorId: String,
      donorName: String,
      patientName: String,
      hospitalName: String,
      bloodGroup: String,
      units: Number,
      urgency: String
    },
    isRead: {
      type: Boolean,
      default: false,
      index: true
    },
    isDeleted: {
      type: Boolean,
      default: false,
      index: true
    }
  },
  {
    timestamps: true
  }
);

// Auto-expire old notifications after 60 days
notificationSchema.index({ createdAt: 1 }, { expireAfterSeconds: 60 * 24 * 60 * 60 });

module.exports = mongoose.model('Notification', notificationSchema);
