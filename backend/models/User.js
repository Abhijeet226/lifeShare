const mongoose = require('mongoose');

const userSchema = new mongoose.Schema({
  name: { type: String, default: '' },
  firstName: { type: String, default: '' },
  lastName: { type: String, default: '' },
  dob: { type: String, default: '' },
  gender: { type: String, default: 'Male' },
  email: {
    type: String,
    required: [true, 'Email is required'],
    unique: true,
    lowercase: true,
    trim: true,
    match: [/^\S+@\S+\.\S+$/, 'Please use a valid email address']
  },
  password: { type: String, required: [true, 'Password is required'] },
  mobile: { type: String, required: [true, 'Mobile number is required'] },
  bloodGroup: {
    type: String,
    required: [true, 'Blood group is required'],
    enum: ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-']
  },

  // Trust & Verification Model (Separate from Account Enforcement)
  verificationStatus: {
    type: String,
    enum: ['UNVERIFIED', 'PHONE_VERIFIED', 'IDENTITY_VERIFIED', 'DONOR_VERIFIED'],
    default: 'UNVERIFIED'
  },
  accountStatus: {
    type: String,
    enum: ['ACTIVE', 'SUSPENDED', 'BLOCKED'],
    default: 'ACTIVE'
  },
  phoneVerified: { type: Boolean, default: false },
  emailVerified: { type: Boolean, default: false },
  verifiedAt: { type: Date, default: null },

  // Geospatial Point: coordinates MUST be [longitude, latitude]
  location: {
    type: {
      type: String,
      enum: ['Point']
    },
    coordinates: {
      type: [Number],
      validate: {
        validator: function (val) {
          if (!val || val.length === 0) return true;
          if (val.length !== 2) return false;
          const [lng, lat] = val;
          return lng >= -180 && lng <= 180 && lat >= -90 && lat <= 90;
        },
        message: 'Coordinates must be valid [longitude, latitude] between -180..180 and -90..90'
      }
    }
  },
  locationUpdatedAt: { type: Date, default: null },

  isAvailable: { type: Boolean, default: true },
  availabilityUpdatedAt: { type: Date, default: Date.now },
  lastActiveAt: { type: Date, default: Date.now },
  lastDonationDate: { type: Date, default: null },
  donationsCount: { type: Number, default: 0 },

  // Privacy and security preferences
  hideMobileNumber: { type: Boolean, default: false },
  biometricEnabled: { type: Boolean, default: true },
  hospitalOnlyVisibility: { type: Boolean, default: false },
  donorId: { type: String, default: '' },
  role: { type: String, enum: ['DONOR', 'COORDINATOR', 'ADMIN'], default: 'DONOR' },
  hospitalId: { type: mongoose.Schema.Types.ObjectId, ref: 'Hospital', default: null },
  cityId: { type: mongoose.Schema.Types.ObjectId, ref: 'City', default: null }
}, {
  timestamps: true,
  toJSON: {
    transform: function (doc, ret) {
      delete ret.password;
      return ret;
    }
  }
});

// Geospatial 2dsphere index for location queries
userSchema.index({ location: '2dsphere' });
userSchema.index({ accountStatus: 1, isAvailable: 1, bloodGroup: 1 });
userSchema.index({ verificationStatus: 1 });
userSchema.index({ lastDonationDate: 1 });
userSchema.index({ cityId: 1 });

const User = mongoose.models.User || mongoose.model('User', userSchema);

module.exports = User;
