const mongoose = require('mongoose');

const pointSchema = new mongoose.Schema({
  type: {
    type: String,
    enum: ['Point'],
    default: 'Point'
  },
  coordinates: {
    type: [Number], // [longitude, latitude]
    required: true
  }
}, { _id: false });

const emergencyRequestSchema = new mongoose.Schema({
  requester: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    default: null
  },
  patientName: { type: String, required: [true, 'Patient name is required'], trim: true },
  bloodGroup: {
    type: String,
    required: [true, 'Blood group is required'],
    enum: ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-']
  },
  unitsRequired: { type: Number, default: 1, min: 1 },
  unitsNeeded: { type: Number, default: 1, min: 1 }, // Backwards compatibility alias
  acceptedCount: { type: Number, default: 0, min: 0 },
  unitsFulfilled: { type: Number, default: 0, min: 0 },

  // Hospital Authority
  hospitalId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Hospital',
    default: null
  },
  hospital: { type: String, required: [true, 'Hospital name is required'], trim: true },
  hospitalName: { type: String, default: '' },
  hospitalAddress: { type: String, default: '' },
  isAuthoritativeHospital: { type: Boolean, default: false },

  hospitalLocation: { type: pointSchema, default: undefined },
  requestLocation: { type: pointSchema, default: undefined },
  cityId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'City',
    default: null
  },
  contactNumber: { type: String, required: [true, 'Contact number is required'] },
  postedBy: { type: String, default: '' },
  urgency: {
    type: String,
    enum: ['NORMAL', 'URGENT', 'CRITICAL'],
    default: 'URGENT'
  },
  status: {
    type: String,
    enum: [
      'REQUESTED',
      'SEARCHING',
      'DONORS_NOTIFIED',
      'PARTIALLY_ACCEPTED',
      'DONOR_RESPONDED',
      'DONOR_ACCEPTED',
      'DONORS_ACCEPTED',
      'ACCEPTED',
      'PARTIALLY_FULFILLED',
      'FULFILLED',
      'DONOR_AT_HOSPITAL',
      'DONATION_COMPLETED',
      'COMPLETED',
      'CANCELLED',
      'EXPIRED',
      'NO_DONOR_FOUND'
    ],
    default: 'SEARCHING'
  },
  searchRadiusMeters: { type: Number, default: 10000 },
  isFulfilled: { type: Boolean, default: false },
  fulfilledAt: { type: Date, default: null },
  cancelledAt: { type: Date, default: null },
  cancelledBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    default: null
  },
  cancelledByRole: {
    type: String,
    enum: ['REQUESTER', 'ADMIN', 'SYSTEM', null],
    default: null
  },
  cancelledReason: { type: String, default: null },
  expiresAt: {
    type: Date,
    default: () => new Date(Date.now() + 24 * 60 * 60 * 1000) // Default 24 hours expiry
  }
}, {
  timestamps: true
});

emergencyRequestSchema.index({ requestLocation: '2dsphere' });
emergencyRequestSchema.index({ hospitalLocation: '2dsphere' });
emergencyRequestSchema.index({ status: 1, createdAt: -1 });
emergencyRequestSchema.index({ bloodGroup: 1, isFulfilled: 1 });
emergencyRequestSchema.index({ hospitalId: 1 });
emergencyRequestSchema.index({ cityId: 1 });

const EmergencyRequest = mongoose.models.EmergencyRequest || mongoose.model('EmergencyRequest', emergencyRequestSchema);

module.exports = EmergencyRequest;
