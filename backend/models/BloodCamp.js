const mongoose = require('mongoose');

const bloodCampSchema = new mongoose.Schema({
  title: {
    type: String,
    required: true,
    trim: true
  },
  organizerName: {
    type: String,
    required: true,
    trim: true
  },
  hospitalId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Hospital',
    default: null
  },
  venueAddress: {
    type: String,
    required: true,
    trim: true
  },
  cityId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'City',
    default: null
  },
  cityName: {
    type: String,
    default: 'Bhubaneswar'
  },
  location: {
    type: {
      type: String,
      enum: ['Point'],
      default: 'Point'
    },
    coordinates: {
      type: [Number], // [longitude, latitude]
      default: [85.8245, 20.2961]
    }
  },
  startDate: {
    type: Date,
    required: true
  },
  endDate: {
    type: Date,
    required: true
  },
  targetUnits: {
    type: Number,
    default: 50
  },
  collectedUnits: {
    type: Number,
    default: 0
  },
  contactPhone: {
    type: String,
    default: ''
  },
  status: {
    type: String,
    enum: ['UPCOMING', 'ONGOING', 'COMPLETED', 'CANCELLED'],
    default: 'UPCOMING'
  },
  rsvps: [{
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User'
    },
    joinedAt: {
      type: Date,
      default: Date.now
    }
  }],
  createdBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User'
  }
}, {
  timestamps: true
});

bloodCampSchema.index({ location: '2dsphere' });
bloodCampSchema.index({ status: 1, startDate: 1 });
bloodCampSchema.index({ status: 1, cityId: 1 });

module.exports = mongoose.model('BloodCamp', bloodCampSchema);
