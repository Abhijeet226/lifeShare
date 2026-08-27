const mongoose = require('mongoose');

const hospitalSchema = new mongoose.Schema({
  name: { type: String, required: true, trim: true },
  address: { type: String, required: true },
  location: {
    type: {
      type: String,
      enum: ['Point'],
      default: 'Point'
    },
    coordinates: {
      type: [Number], // [longitude, latitude]
      required: true
    }
  },
  phone: { type: String, default: '' },
  verified: { type: Boolean, default: true },
  isVerified: { type: Boolean, default: true },
  emergencySupport: { type: Boolean, default: true },
  cityId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'City',
    default: null
  },
  authorizedCoordinatorIds: [{
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User'
  }],
  coordinatorHistory: [{
    coordinatorId: { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
    name: { type: String, default: 'Coordinator' },
    email: { type: String, default: '' },
    mobile: { type: String, default: '' },
    staffId: { type: String, default: '' },
    assignedAt: { type: Date, default: Date.now },
    assignedBy: { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
    revokedAt: { type: Date, default: null },
    revokedBy: { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
    reason: { type: String, default: '' },
    donationsVerifiedCount: { type: Number, default: 0 }
  }],
  doctors: [{
    name: { type: String, required: true, trim: true },
    designation: { type: String, default: 'Medical Officer (Transfusion Medicine)' },
    registrationNumber: { type: String, required: true, trim: true },
    department: { type: String, default: 'Blood Bank & Transfusion Unit' },
    phone: { type: String, default: '' },
    email: { type: String, default: '' },
    isActive: { type: Boolean, default: true },
    addedAt: { type: Date, default: Date.now },
    addedBy: { type: mongoose.Schema.Types.ObjectId, ref: 'User' }
  }]
}, {
  timestamps: true
});

hospitalSchema.index({ location: '2dsphere' });
hospitalSchema.index({ cityId: 1 });
hospitalSchema.index({ authorizedCoordinatorIds: 1 });

const Hospital = mongoose.models.Hospital || mongoose.model('Hospital', hospitalSchema);

module.exports = Hospital;
