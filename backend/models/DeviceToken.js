const mongoose = require('mongoose');

const deviceTokenSchema = new mongoose.Schema({
  userId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true,
    index: true
  },
  token: {
    type: String,
    required: true,
    unique: true,
    trim: true
  },
  platform: {
    type: String,
    enum: ['ANDROID', 'IOS', 'WEB'],
    default: 'ANDROID'
  }
}, {
  timestamps: true
});

deviceTokenSchema.index({ userId: 1, platform: 1 });

const DeviceToken = mongoose.models.DeviceToken || mongoose.model('DeviceToken', deviceTokenSchema);

module.exports = DeviceToken;
