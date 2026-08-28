const mongoose = require('mongoose');

const chatMessageSchema = new mongoose.Schema({
  emergencyRequestId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'EmergencyRequest',
    required: true,
    index: true
  },
  senderId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  senderName: {
    type: String,
    required: true,
    trim: true
  },
  senderRole: {
    type: String,
    enum: ['REQUESTER', 'DONOR', 'COORDINATOR', 'ADMIN'],
    default: 'DONOR'
  },
  messageType: {
    type: String,
    enum: ['TEXT', 'ETA_UPDATE', 'LOCATION_UPDATE', 'STATUS_CHANGE', 'COORDINATOR_DIRECTIVE'],
    default: 'TEXT'
  },
  messageText: {
    type: String,
    required: true,
    trim: true
  },
  etaMinutes: {
    type: Number,
    default: null
  },
  distanceKm: {
    type: Number,
    default: null
  },
  donorCoordinates: {
    type: [Number], // [longitude, latitude]
    default: undefined
  },
  readBy: [{
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User'
  }]
}, {
  timestamps: true
});

chatMessageSchema.index({ emergencyRequestId: 1, createdAt: 1 });

const ChatMessage = mongoose.models.ChatMessage || mongoose.model('ChatMessage', chatMessageSchema);

module.exports = ChatMessage;
