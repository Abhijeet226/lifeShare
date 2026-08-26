const mongoose = require('mongoose');

const citySchema = new mongoose.Schema({
  name: {
    type: String,
    required: [true, 'City name is required'],
    trim: true
  },
  normalizedName: {
    type: String,
    required: true,
    unique: true,
    lowercase: true,
    trim: true,
    index: true
  },
  stateName: {
    type: String,
    default: 'Odisha',
    trim: true
  },
  stateCode: {
    type: String,
    default: 'OD',
    trim: true,
    uppercase: true
  },
  countryName: {
    type: String,
    default: 'India',
    trim: true
  },
  countryCode: {
    type: String,
    default: 'IN',
    trim: true,
    uppercase: true
  },
  location: {
    type: {
      type: String,
      enum: ['Point'],
      default: 'Point'
    },
    coordinates: {
      type: [Number], // [longitude, latitude]
      required: true,
      validate: {
        validator: function (val) {
          if (!val || val.length !== 2) return false;
          const [lng, lat] = val;
          return lng >= -180 && lng <= 180 && lat >= -90 && lat <= 90;
        },
        message: 'City coordinates must be valid [longitude, latitude]'
      }
    }
  },
  isActive: {
    type: Boolean,
    default: true,
    index: true
  }
}, {
  timestamps: true
});

// Geospatial 2dsphere index for location-based city matching
citySchema.index({ location: '2dsphere' });

// Ensure normalizedName is always set before saving
citySchema.pre('validate', function (next) {
  if (this.name) {
    this.normalizedName = this.name.trim().toLowerCase();
  }
  next();
});

const City = mongoose.models.City || mongoose.model('City', citySchema);

module.exports = City;
