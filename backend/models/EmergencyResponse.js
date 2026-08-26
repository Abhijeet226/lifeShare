const mongoose = require('mongoose');

const emergencyResponseSchema = new mongoose.Schema({
  requestId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'EmergencyRequest',
    required: true,
    index: true
  },
  donorId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true,
    index: true
  },
  status: {
    type: String,
    enum: ['NOTIFIED', 'VIEWED', 'ACCEPTED', 'DECLINED', 'TRAVELLING', 'ARRIVED', 'DONATED', 'CANCELLED', 'COMPLETED'],
    default: 'NOTIFIED'
  },
  notifiedAt: { type: Date, default: Date.now },
  viewedAt: { type: Date, default: null },
  respondedAt: { type: Date, default: null },
  acceptedAt: { type: Date, default: null },
  travellingAt: { type: Date, default: null },
  arrivedAt: { type: Date, default: null },
  donatedAt: { type: Date, default: null },
  completedAt: { type: Date, default: null },
  cancelledAt: { type: Date, default: null }
}, {
  timestamps: true
});

// Unique compound index: prevents duplicate responses from same donor for same emergency
emergencyResponseSchema.index({ requestId: 1, donorId: 1 }, { unique: true });
emergencyResponseSchema.index({ donorId: 1, status: 1 });

const EmergencyResponse = mongoose.models.EmergencyResponse || mongoose.model('EmergencyResponse', emergencyResponseSchema);

module.exports = EmergencyResponse;
