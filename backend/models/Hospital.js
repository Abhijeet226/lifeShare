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
  }]
}, {
  timestamps: true
});

hospitalSchema.index({ location: '2dsphere' });
hospitalSchema.index({ cityId: 1 });
hospitalSchema.index({ authorizedCoordinatorIds: 1 });

const Hospital = mongoose.models.Hospital || mongoose.model('Hospital', hospitalSchema);

module.exports = Hospital;
