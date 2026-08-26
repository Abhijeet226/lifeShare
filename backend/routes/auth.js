const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const User = require('../models/User');
const City = require('../models/City');
const OTP = require('../models/OTP');
const { JWT_SECRET, authenticateToken } = require('../middleware/auth');
const { sendOtpEmail } = require('../services/emailService');
const { sendOtpSms } = require('../services/smsService');

function sanitizeUser(user) {
  const cityName = user.cityId && user.cityId.name ? user.cityId.name : (user.city || 'Bhubaneswar');
  const resolvedCityId = user.cityId ? (user.cityId._id ? user.cityId._id.toString() : user.cityId.toString()) : null;
  return {
    id: user._id,
    name: user.name,
    firstName: user.firstName,
    lastName: user.lastName,
    dob: user.dob,
    gender: user.gender,
    email: user.email,
    mobile: user.mobile,
    bloodGroup: user.bloodGroup,
    city: cityName,
    cityId: resolvedCityId,
    isAvailable: user.isAvailable,
    donorId: user.donorId || '',
    role: user.role || 'DONOR',
    hospitalId: user.hospitalId || null,
    verificationStatus: user.verificationStatus || 'UNVERIFIED',
    accountStatus: user.accountStatus || 'ACTIVE',
    phoneVerified: !!user.phoneVerified,
    emailVerified: !!user.emailVerified,
    donationsCount: user.donationsCount || 0
  };
}

// POST /api/auth/send-signup-otp (Send real Email or SMS OTP for Signup)
router.post('/send-signup-otp', async (req, res) => {
  try {
    const { identifier, type } = req.body; // mobile or email
    if (!identifier) {
      return res.status(400).json({ success: false, message: 'Email or Mobile number is required' });
    }

    const cleanIdentifier = identifier.trim().toLowerCase();
    const generatedOtp = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10 mins

    await OTP.deleteMany({ email: cleanIdentifier });
    await new OTP({ email: cleanIdentifier, otp: generatedOtp, expiresAt }).save();

    console.log(`🔑 [SIGNUP OTP] For ${cleanIdentifier} (${type || 'PHONE'}): ${generatedOtp}`);

    if (type === 'EMAIL' || cleanIdentifier.includes('@')) {
      const mailRes = await sendOtpEmail(cleanIdentifier, generatedOtp, 'Signup Registration');
      return res.json({
        success: true,
        message: mailRes.simulated
          ? `Verification OTP is ${generatedOtp} (Simulated - set SMTP in .env for real email)`
          : `Verification code sent to your email inbox (${cleanIdentifier})`
      });
    } else {
      const smsRes = await sendOtpSms(cleanIdentifier, generatedOtp, 'Signup Registration');
      return res.json({
        success: true,
        message: smsRes.simulated
          ? `Verification OTP is ${generatedOtp} (Simulated - set FAST2SMS/TWILIO in .env for real SMS)`
          : `Verification code sent via SMS to ${cleanIdentifier}`
      });
    }
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// POST /api/auth/verify-signup-otp (Verify Signup OTP for Email or Mobile)
router.post('/verify-signup-otp', async (req, res) => {
  try {
    const { identifier, otp } = req.body;
    if (!identifier || !otp) {
      return res.status(400).json({ success: false, message: 'Identifier and OTP are required' });
    }

    const checkId = identifier.trim().toLowerCase();
    const otpRecord = await OTP.findOne({ email: checkId, otp: otp.trim() });
    if (!otpRecord || new Date() > otpRecord.expiresAt) {
      return res.status(400).json({ success: false, message: 'Invalid or expired OTP code' });
    }

    res.json({ success: true, message: 'OTP verified successfully' });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// POST /api/auth/register
router.post('/register', async (req, res) => {
  try {
    const {
      name,
      firstName,
      lastName,
      dob,
      gender,
      email,
      password,
      mobile,
      bloodGroup,
      city,
      cityId,
      isAvailable,
      latitude,
      longitude,
      otp // Optional OTP for signup verification
    } = req.body;

    if (!email || !password) {
      return res.status(400).json({ success: false, message: 'Email and password are required' });
    }
    const cleanEmail = email.toLowerCase().trim();
    const existing = await User.findOne({ email: cleanEmail });
    if (existing) {
      return res.status(400).json({ success: false, message: 'Email is already registered' });
    }

    const resolvedFirst = firstName || (name ? name.split(' ')[0] : 'Donor');
    const resolvedLast = lastName || (name && name.includes(' ') ? name.substring(name.indexOf(' ') + 1) : '');
    const fullName = name || `${resolvedFirst} ${resolvedLast}`.trim();
    const hashedPassword = await bcrypt.hash(password, 10);

    let phoneVerified = false;
    let verificationStatus = 'UNVERIFIED';

    // Optional Signup OTP Verification check
    if (otp && (mobile || cleanEmail)) {
      const checkId = (mobile || cleanEmail).trim().toLowerCase();
      const otpRecord = await OTP.findOne({ email: checkId, otp: otp.trim() });
      if (otpRecord && new Date() <= otpRecord.expiresAt) {
        phoneVerified = true;
        verificationStatus = 'PHONE_VERIFIED';
        await OTP.deleteMany({ email: checkId });
      }
    }

    let resolvedCityId = null;
    let resolvedCityName = city || 'Bhubaneswar';
    let defaultCoordinates = null;

    if (cityId) {
      const cityDoc = await City.findById(cityId);
      if (cityDoc) {
        resolvedCityId = cityDoc._id;
        resolvedCityName = cityDoc.name;
        if (cityDoc.location && cityDoc.location.coordinates) {
          defaultCoordinates = cityDoc.location.coordinates;
        }
      }
    } else if (city) {
      const cityDoc = await City.findOne({ normalizedName: city.trim().toLowerCase() });
      if (cityDoc) {
        resolvedCityId = cityDoc._id;
        resolvedCityName = cityDoc.name;
        if (cityDoc.location && cityDoc.location.coordinates) {
          defaultCoordinates = cityDoc.location.coordinates;
        }
      }
    }

    const userData = {
      name: fullName,
      firstName: resolvedFirst,
      lastName: resolvedLast,
      dob: dob || '',
      gender: gender || 'Male',
      email: cleanEmail,
      password: hashedPassword,
      mobile: mobile || '+91 ',
      bloodGroup: bloodGroup || 'O+',
      city: resolvedCityName,
      cityId: resolvedCityId,
      isAvailable: isAvailable !== undefined ? isAvailable : true,
      verificationStatus,
      accountStatus: 'ACTIVE',
      phoneVerified,
      emailVerified: false,
      verifiedAt: phoneVerified ? new Date() : null
    };

    if (typeof latitude === 'number' && typeof longitude === 'number') {
      userData.location = {
        type: 'Point',
        coordinates: [longitude, latitude] // GeoJSON order: [lng, lat]
      };
      userData.locationUpdatedAt = new Date();
    } else if (defaultCoordinates) {
      userData.location = {
        type: 'Point',
        coordinates: defaultCoordinates
      };
      userData.locationUpdatedAt = new Date();
    }

    const user = new User(userData);
    await user.save();
    await user.populate('cityId', 'name stateName location');

    const token = jwt.sign({ id: user._id, email: user.email }, JWT_SECRET, { expiresIn: '30d' });
    res.status(201).json({
      success: true,
      token,
      user: sanitizeUser(user)
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// POST /api/auth/login
router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;
    const cleanEmail = (email || '').toLowerCase().trim();
    const user = await User.findOne({ email: cleanEmail }).populate('cityId', 'name stateName location');
    if (!user) {
      return res.status(401).json({ success: false, message: 'No account found with this email' });
    }

    if (user.accountStatus === 'SUSPENDED' || user.accountStatus === 'BLOCKED') {
      return res.status(403).json({
        success: false,
        message: `Account is ${user.accountStatus.toLowerCase()}. Access denied.`
      });
    }

    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) {
      return res.status(401).json({ success: false, message: 'Invalid password' });
    }

    // Update last active timestamp
    user.lastActiveAt = new Date();
    await user.save();

    const token = jwt.sign({ id: user._id, email: user.email }, JWT_SECRET, { expiresIn: '30d' });
    res.json({
      success: true,
      token,
      user: sanitizeUser(user)
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// POST /api/auth/google-login
router.post('/google-login', async (req, res) => {
  try {
    const { name, firstName, lastName, dob, gender, email, googleId, mobile, bloodGroup, city, cityId, latitude, longitude } = req.body;
    if (!email) {
      return res.status(400).json({ success: false, message: 'Google email is required' });
    }
    const cleanEmail = email.toLowerCase().trim();
    let user = await User.findOne({ email: cleanEmail }).populate('cityId', 'name stateName location');

    if (!user) {
      const resolvedFirst = firstName || (name ? name.split(' ')[0] : 'Google');
      const resolvedLast = lastName || (name && name.includes(' ') ? name.substring(name.indexOf(' ') + 1) : 'Donor');
      const fullName = name || `${resolvedFirst} ${resolvedLast}`.trim();
      const randomPassword = await bcrypt.hash((googleId || 'google_auth_pass') + Date.now(), 10);

      let resolvedCityId = null;
      let defaultCoordinates = null;
      if (cityId) {
        const cityDoc = await City.findById(cityId);
        if (cityDoc) {
          resolvedCityId = cityDoc._id;
          if (cityDoc.location && cityDoc.location.coordinates) {
            defaultCoordinates = cityDoc.location.coordinates;
          }
        }
      } else if (city) {
        const cityDoc = await City.findOne({ normalizedName: city.trim().toLowerCase() });
        if (cityDoc) {
          resolvedCityId = cityDoc._id;
          if (cityDoc.location && cityDoc.location.coordinates) {
            defaultCoordinates = cityDoc.location.coordinates;
          }
        }
      }

      const userData = {
        name: fullName,
        firstName: resolvedFirst,
        lastName: resolvedLast,
        dob: dob || '',
        gender: gender || 'Male',
        email: cleanEmail,
        password: randomPassword,
        mobile: mobile || '+91 ',
        bloodGroup: bloodGroup || 'O+',
        cityId: resolvedCityId,
        isAvailable: true,
        verificationStatus: 'UNVERIFIED',
        accountStatus: 'ACTIVE',
        phoneVerified: false,
        emailVerified: true // Google OAuth verified email
      };

      if (typeof latitude === 'number' && typeof longitude === 'number') {
        userData.location = {
          type: 'Point',
          coordinates: [longitude, latitude]
        };
        userData.locationUpdatedAt = new Date();
      } else if (defaultCoordinates) {
        userData.location = {
          type: 'Point',
          coordinates: defaultCoordinates
        };
        userData.locationUpdatedAt = new Date();
      }

      user = new User(userData);
      await user.save();
      await user.populate('cityId', 'name stateName location');
    } else {
      if (user.accountStatus === 'SUSPENDED' || user.accountStatus === 'BLOCKED') {
        return res.status(403).json({
          success: false,
          message: `Account is ${user.accountStatus.toLowerCase()}. Access denied.`
        });
      }
      user.lastActiveAt = new Date();
      await user.save();
    }

    const token = jwt.sign({ id: user._id, email: user.email }, JWT_SECRET, { expiresIn: '30d' });
    res.json({
      success: true,
      token,
      user: sanitizeUser(user)
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// POST /api/auth/send-verification-otp (Authenticated: Send real OTP for Phone or Email verification)
router.post('/send-verification-otp', authenticateToken, async (req, res) => {
  try {
    const { type, targetIdentifier } = req.body; // 'PHONE' or 'EMAIL'
    const user = await User.findById(req.user.id);
    if (!user) {
      return res.status(404).json({ success: false, message: 'User account not found' });
    }

    // Support verification of a new phone/email or existing registered one
    let identifier = targetIdentifier ? targetIdentifier.trim() : (type === 'EMAIL' ? user.email.toLowerCase().trim() : user.mobile.trim());
    if (type === 'EMAIL') {
      identifier = identifier.toLowerCase();
    }

    const generatedOtp = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10 minutes

    await OTP.deleteMany({ email: identifier });
    await new OTP({ email: identifier, otp: generatedOtp, expiresAt }).save();

    console.log(`🔑 [ACCOUNT VERIFICATION OTP] For ${user.name} (${identifier}) [${type || 'PHONE'}]: ${generatedOtp}`);

    if (type === 'EMAIL' || identifier.includes('@')) {
      const mailRes = await sendOtpEmail(identifier, generatedOtp, 'Account Email Verification');
      return res.json({
        success: true,
        message: mailRes.simulated
          ? `Email verification code is ${generatedOtp} (Simulated - set SMTP in .env for real email)`
          : `Verification code delivered to ${identifier}`
      });
    } else {
      const smsRes = await sendOtpSms(identifier, generatedOtp, 'Phone Number Verification');
      return res.json({
        success: true,
        message: smsRes.simulated
          ? `Phone verification code is ${generatedOtp} (Simulated - set FAST2SMS/TWILIO in .env for real SMS)`
          : `Verification SMS sent to ${identifier}`
      });
    }
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// POST /api/auth/verify-account-otp (Authenticated: Validate OTP and mark user/new number as verified)
router.post('/verify-account-otp', authenticateToken, async (req, res) => {
  try {
    const { type, otp, targetIdentifier } = req.body; // 'PHONE' or 'EMAIL'
    const user = await User.findById(req.user.id);
    if (!user) {
      return res.status(404).json({ success: false, message: 'User account not found' });
    }

    let identifier = targetIdentifier ? targetIdentifier.trim() : (type === 'EMAIL' ? user.email.toLowerCase().trim() : user.mobile.trim());
    if (type === 'EMAIL') {
      identifier = identifier.toLowerCase();
    }

    const record = await OTP.findOne({ email: identifier, otp: (otp || '').trim() });

    if (!record) {
      return res.status(400).json({ success: false, message: 'Invalid or expired verification code' });
    }

    if (new Date() > record.expiresAt) {
      await OTP.deleteOne({ _id: record._id });
      return res.status(400).json({ success: false, message: 'Verification code has expired. Please request a new one.' });
    }

    if (type === 'EMAIL') {
      if (targetIdentifier && targetIdentifier.toLowerCase().trim() !== user.email) {
        user.email = targetIdentifier.toLowerCase().trim();
      }
      user.emailVerified = true;
    } else {
      if (targetIdentifier && targetIdentifier.trim() !== user.mobile) {
        user.mobile = targetIdentifier.trim();
      }
      user.phoneVerified = true;
      user.verificationStatus = 'PHONE_VERIFIED';
    }
    user.verifiedAt = new Date();
    await user.save();
    await OTP.deleteMany({ email: identifier });

    res.json({
      success: true,
      message: `${type === 'EMAIL' ? 'Email' : 'Phone number'} verified successfully!`,
      user: sanitizeUser(user)
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// POST /api/auth/forgot-password & /api/auth/send-otp
const handleForgotPassword = async (req, res) => {
  try {
    const { email } = req.body;
    const cleanEmail = (email || '').toLowerCase().trim();
    const user = await User.findOne({ email: cleanEmail });
    if (!user) {
      return res.status(404).json({ success: false, message: 'Email address is not registered' });
    }

    const generatedOtp = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10 minutes

    await OTP.deleteMany({ email: cleanEmail });
    await new OTP({ email: cleanEmail, otp: generatedOtp, expiresAt }).save();

    console.log(`🔑 [PASSWORD RESET OTP] For ${cleanEmail}: ${generatedOtp}`);

    const mailRes = await sendOtpEmail(cleanEmail, generatedOtp, 'Password Recovery');
    if (user.mobile && user.mobile.length >= 10) {
      sendOtpSms(user.mobile, generatedOtp, 'Password Recovery').catch(() => {});
    }

    res.json({
      success: true,
      message: mailRes.simulated
        ? `Password recovery OTP is ${generatedOtp} (Simulated - set SMTP in .env for real email)`
        : 'Verification OTP sent to your registered email address.'
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
};

router.post('/forgot-password', handleForgotPassword);
router.post('/send-otp', handleForgotPassword);

// POST /api/auth/verify-reset-otp & /api/auth/verify-otp
const handleVerifyOtp = async (req, res) => {
  try {
    const { email, otp, newPassword } = req.body;
    const cleanEmail = (email || '').toLowerCase().trim();

    if (!newPassword || newPassword.length < 6) {
      return res.status(400).json({ success: false, message: 'New password must be at least 6 characters' });
    }

    const record = await OTP.findOne({ email: cleanEmail, otp: (otp || '').trim() });
    if (!record) {
      return res.status(400).json({ success: false, message: 'Invalid or expired OTP code' });
    }

    if (new Date() > record.expiresAt) {
      await OTP.deleteOne({ _id: record._id });
      return res.status(400).json({ success: false, message: 'OTP has expired. Please request a new one.' });
    }

    const hashedPassword = await bcrypt.hash(newPassword, 10);
    await User.updateOne({ email: cleanEmail }, {
      password: hashedPassword,
      phoneVerified: true,
      emailVerified: true,
      verificationStatus: 'PHONE_VERIFIED',
      verifiedAt: new Date()
    });
    await OTP.deleteMany({ email: cleanEmail });

    res.json({ success: true, message: 'Password has been reset and account verified. You can now sign in.' });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
};

router.post('/verify-reset-otp', handleVerifyOtp);
router.post('/verify-otp', handleVerifyOtp);

module.exports = router;
