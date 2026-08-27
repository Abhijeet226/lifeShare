const mongoose = require('mongoose');

const donationHistorySchema = new mongoose.Schema({
  donorId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true,
    index: true
  },
  requestId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'EmergencyRequest',
    required: true,
    index: true
  },
  hospitalId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Hospital',
    default: null
  },
  hospital: {
    type: String,
    required: true
  },
  patientName: {
    type: String,
    default: ''
  },
  bloodGroup: {
    type: String,
    required: true
  },
  unitsDonated: {
    type: Number,
    default: 1
  },
  donationDate: {
    type: Date,
    default: Date.now
  },
  status: {
    type: String,
    enum: ['VERIFIED', 'CANCELLED'],
    default: 'VERIFIED'
  },
  attendingDoctor: {
    type: String,
    default: 'Attending Medical Officer'
  },
  doctorRegistrationNo: {
    type: String,
    default: ''
  },
  verifiedBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  verifiedAt: {
    type: Date,
    default: Date.now
  },
  certificateId: {
    type: String,
    unique: true,
    required: true,
    index: true
  },
  certificateHash: {
    type: String,
    required: true
  }
}, {
  timestamps: true
});

// Compound unique index: guarantees strictly 1 verified donation per emergency request and donor
donationHistorySchema.index({ requestId: 1, donorId: 1 }, { unique: true });
donationHistorySchema.index({ donorId: 1, donationDate: -1 });

const DonationHistory = mongoose.models.DonationHistory || mongoose.model('DonationHistory', donationHistorySchema);

module.exports = DonationHistory;
