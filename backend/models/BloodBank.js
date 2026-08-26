const mongoose = require('mongoose');

const bloodBankSchema = new mongoose.Schema({
  name: { type: String, required: true, trim: true },
  address: { type: String, required: true },
  cityId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'City',
    default: null
  },
  contactNumber: { type: String, required: true },
  availableUnits: { type: Number, default: 10 },
  timings: { type: String, default: '24x7 Open' },
  type: { type: String, default: 'Blood Bank' },
  location: {
    type: {
      type: String,
      enum: ['Point'],
      default: 'Point'
    },
    coordinates: {
      type: [Number], // [longitude, latitude]
      default: undefined
    }
  },
  verified: { type: Boolean, default: true }
}, {
  timestamps: true
});

bloodBankSchema.index({ location: '2dsphere' });
bloodBankSchema.index({ cityId: 1 });

const BloodBank = mongoose.models.BloodBank || mongoose.model('BloodBank', bloodBankSchema);

module.exports = BloodBank;
