const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');
const User = require('../models/User');
const City = require('../models/City');
const DonationHistory = require('../models/DonationHistory');
const { authenticateToken } = require('../middleware/auth');
const { isValidCoordinate, getCityCoordinates } = require('../services/locationService');
const { checkDonorEligibility } = require('../services/cooldownService');

function sanitizeUser(user) {
  const cityName = user.cityId && user.cityId.name ? user.cityId.name : (user.city || 'Bhubaneswar');
  const resolvedCityId = user.cityId ? (user.cityId._id ? user.cityId._id.toString() : user.cityId.toString()) : null;
  const eligibility = checkDonorEligibility(user);
  const effectiveAvailability = eligibility.isEligible ? !!user.isAvailable : false;

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
    isAvailable: effectiveAvailability,
    donorId: user.donorId || '',
    role: user.role || 'DONOR',
    hospitalId: user.hospitalId || null,
    verificationStatus: user.verificationStatus || 'UNVERIFIED',
    accountStatus: user.accountStatus || 'ACTIVE',
    phoneVerified: !!user.phoneVerified,
    emailVerified: !!user.emailVerified,
    donationsCount: user.donationsCount || 0,
    karmaPoints: user.karmaPoints || 0,
    badges: user.badges || [],
    lastDonationDate: user.lastDonationDate || (eligibility ? eligibility.lastDonationDate : null),
    eligibility: eligibility,
    hideMobileNumber: user.hideMobileNumber,
    biometricEnabled: user.biometricEnabled,
    hospitalOnlyVisibility: user.hospitalOnlyVisibility,
    locationUpdatedAt: user.locationUpdatedAt
  };
}

// GET /api/users/me
router.get('/me', authenticateToken, async (req, res) => {
  try {
    const user = await User.findById(req.user.id).populate('cityId', 'name stateName location');
    if (!user) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    // Auto-update last donation date from DonationHistory if available
    const latestDonation = await DonationHistory.findOne({ donorId: user._id }).sort({ donationDate: -1 });
    if (latestDonation && (!user.lastDonationDate || new Date(latestDonation.donationDate) > new Date(user.lastDonationDate))) {
      user.lastDonationDate = latestDonation.donationDate;
      const count = await DonationHistory.countDocuments({ donorId: user._id });
      if (count > (user.donationsCount || 0)) {
        user.donationsCount = count;
      }
      await user.save();
    }

    // If in 90-day cooldown, ensure isAvailable is false
    const eligibility = checkDonorEligibility(user);
    if (!eligibility.isEligible && user.isAvailable) {
      user.isAvailable = false;
      await user.save();
    }

    res.json({ success: true, user: sanitizeUser(user) });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// PUT /api/users/profile
router.put('/profile', authenticateToken, async (req, res) => {
  try {
    const { name, firstName, lastName, dob, gender, mobile, city, cityId, bloodGroup, isAvailable, hideMobileNumber, biometricEnabled, hospitalOnlyVisibility, donorId, latitude, longitude } = req.body;

    const currentUser = await User.findById(req.user.id);
    if (!currentUser) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    // Check cooldown eligibility
    const eligibility = checkDonorEligibility(currentUser);

    const resolvedFirst = firstName || (name ? name.split(' ')[0] : '');
    const resolvedLast = lastName || (name && name.includes(' ') ? name.substring(name.indexOf(' ') + 1) : '');
    const fullName = name || `${resolvedFirst} ${resolvedLast}`.trim();

    const updateFields = {
      name: fullName,
      firstName: resolvedFirst,
      lastName: resolvedLast,
      dob: dob !== undefined ? dob : undefined,
      gender: gender !== undefined ? gender : undefined,
      mobile: mobile !== undefined ? mobile : undefined,
      cityId: cityId !== undefined ? cityId : undefined,
      bloodGroup: bloodGroup !== undefined ? bloodGroup : undefined,
      isAvailable: isAvailable !== undefined ? (eligibility.isEligible ? isAvailable : false) : undefined,
      hideMobileNumber: hideMobileNumber !== undefined ? hideMobileNumber : undefined,
      biometricEnabled: biometricEnabled !== undefined ? biometricEnabled : undefined,
      hospitalOnlyVisibility: hospitalOnlyVisibility !== undefined ? hospitalOnlyVisibility : undefined,
      donorId: donorId !== undefined ? donorId : undefined
    };

    if (cityId) {
      const cityDoc = await City.findById(cityId);
      if (cityDoc) {
        updateFields.cityId = cityDoc._id;
        if (!latitude && !longitude && cityDoc.location && cityDoc.location.coordinates) {
          updateFields.location = {
            type: 'Point',
            coordinates: cityDoc.location.coordinates
          };
          updateFields.locationUpdatedAt = new Date();
        }
      }
    } else if (city) {
      const cityDoc = await City.findOne({ normalizedName: city.trim().toLowerCase() });
      if (cityDoc) {
        updateFields.cityId = cityDoc._id;
      }
    }

    // Update location coordinates if provided directly or infer from city
    if (typeof latitude === 'number' && typeof longitude === 'number' && isValidCoordinate(longitude, latitude)) {
      updateFields.location = {
        type: 'Point',
        coordinates: [longitude, latitude]
      };
      updateFields.locationUpdatedAt = new Date();
    } else if (city && !cityId) {
      const cityCoords = getCityCoordinates(city);
      if (cityCoords && !updateFields.location) {
        updateFields.location = {
          type: 'Point',
          coordinates: [cityCoords.lng, cityCoords.lat]
        };
        updateFields.locationUpdatedAt = new Date();
      }
    }

    // Remove undefined values (prevent tampering with verificationStatus or accountStatus)
    Object.keys(updateFields).forEach((key) => updateFields[key] === undefined && delete updateFields[key]);

    const user = await User.findByIdAndUpdate(req.user.id, updateFields, { new: true, runValidators: true })
      .populate('cityId', 'name stateName location');
    if (!user) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    res.json({
      success: true,
      message: 'Profile updated successfully',
      user: sanitizeUser(user)
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// PUT & POST /api/users/change-password
const handleChangePassword = async (req, res) => {
  try {
    const { newPassword } = req.body;
    if (!newPassword || newPassword.length < 6) {
      return res.status(400).json({ success: false, message: 'Password must be at least 6 characters' });
    }

    const hashedPassword = await bcrypt.hash(newPassword, 10);
    await User.findByIdAndUpdate(req.user.id, { password: hashedPassword });

    res.json({ success: true, message: 'Password updated successfully' });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
};

router.put('/change-password', authenticateToken, handleChangePassword);
router.post('/change-password', authenticateToken, handleChangePassword);

// PUT /api/users/location - Update user GPS coordinates
router.put('/location', authenticateToken, async (req, res) => {
  try {
    const { latitude, longitude } = req.body;

    if (!isValidCoordinate(longitude, latitude)) {
      return res.status(400).json({
        success: false,
        message: 'Invalid coordinates. Longitude must be between -180..180 and Latitude between -90..90.'
      });
    }

    const user = await User.findByIdAndUpdate(
      req.user.id,
      {
        location: {
          type: 'Point',
          coordinates: [longitude, latitude] // GeoJSON: [lng, lat]
        },
        locationUpdatedAt: new Date(),
        lastActiveAt: new Date()
      },
      { new: true }
    );

    if (!user) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    res.json({
      success: true,
      message: 'Location updated successfully',
      locationUpdatedAt: user.locationUpdatedAt
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// PUT /api/users/availability - Toggle donor availability
router.put('/availability', authenticateToken, async (req, res) => {
  try {
    const { isAvailable } = req.body;
    if (typeof isAvailable !== 'boolean') {
      return res.status(400).json({ success: false, message: 'isAvailable boolean is required' });
    }

    const user = await User.findByIdAndUpdate(
      req.user.id,
      {
        isAvailable,
        availabilityUpdatedAt: new Date(),
        lastActiveAt: new Date()
      },
      { new: true }
    );

    if (!user) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    res.json({
      success: true,
      message: `Donor availability updated to ${isAvailable ? 'Available' : 'Unavailable'}`,
      isAvailable: user.isAvailable
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// GET /api/users/leaderboard - Community Hall of Fame (Top voluntary lifesavers)
router.get('/leaderboard', async (req, res) => {
  try {
    const { limit = 25, cityId } = req.query;
    const maxLimit = Math.min(parseInt(limit, 10) || 25, 50);

    const query = {
      accountStatus: 'ACTIVE',
      role: 'DONOR'
    };
    if (cityId) {
      query.cityId = cityId;
    }

    const topDonors = await User.find(query)
      .select('name firstName lastName bloodGroup donationsCount karmaPoints badges cityId createdAt')
      .populate('cityId', 'name stateName')
      .sort({ karmaPoints: -1, donationsCount: -1, createdAt: 1 })
      .limit(maxLimit);

    const formatted = topDonors.map((u, index) => {
      // Privacy-safe display: "Rahul S."
      const displayName = u.firstName
        ? `${u.firstName} ${u.lastName ? u.lastName.charAt(0) + '.' : ''}`.trim()
        : (u.name ? u.name.split(' ')[0] : 'Voluntary Donor');

      return {
        rank: index + 1,
        id: u._id,
        displayName,
        bloodGroup: u.bloodGroup,
        city: u.cityId && u.cityId.name ? u.cityId.name : 'Odisha',
        donationsCount: u.donationsCount || 0,
        karmaPoints: u.karmaPoints || 0,
        badgeCount: (u.badges || []).length,
        topBadge: (u.badges && u.badges.length > 0) ? u.badges[u.badges.length - 1] : null
      };
    });

    res.json({
      success: true,
      count: formatted.length,
      leaderboard: formatted
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// POST /api/users/coordinator-onboard-donor - Onboard & physically verify walk-in donor
router.post('/coordinator-onboard-donor', authenticateToken, async (req, res) => {
  try {
    const coordinator = await User.findById(req.user.id);
    if (!coordinator || (coordinator.role !== 'COORDINATOR' && coordinator.role !== 'ADMIN')) {
      return res.status(403).json({ success: false, message: 'Forbidden: Coordinator authority required.' });
    }

    const { name, mobile, bloodGroup, cityId, gender } = req.body;

    if (!name || !name.trim()) {
      return res.status(400).json({ success: false, message: 'Donor full name is required.' });
    }
    if (!mobile || !mobile.trim()) {
      return res.status(400).json({ success: false, message: 'Donor mobile number is required.' });
    }
    if (!bloodGroup || !['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'].includes(bloodGroup)) {
      return res.status(400).json({ success: false, message: 'Valid blood group is required.' });
    }

    const cleanMobile = mobile.trim();
    let existingUser = await User.findOne({ mobile: cleanMobile });

    if (existingUser) {
      // Elevate verification status
      existingUser.verificationStatus = 'DONOR_VERIFIED';
      existingUser.phoneVerified = true;
      existingUser.bloodGroup = bloodGroup;
      if (cityId) existingUser.cityId = cityId;
      await existingUser.save();

      return res.json({
        success: true,
        message: 'Existing donor profile verified and enrolled into live rescue pool.',
        user: sanitizeUser(existingUser)
      });
    }

    // Create new donor account with auto-generated secure credentials
    const dummyEmail = `donor_${cleanMobile.replace(/\D/g, '')}@lifeshare.net`;
    const tempPassword = `LifeShare@${Math.floor(1000 + Math.random() * 9000)}`;
    const hashedPassword = await bcrypt.hash(tempPassword, 10);

    const parts = name.trim().split(' ');
    const firstName = parts[0] || 'Voluntary';
    const lastName = parts.slice(1).join(' ') || 'Donor';

    const newUser = new User({
      name: name.trim(),
      firstName,
      lastName,
      email: dummyEmail,
      password: hashedPassword,
      mobile: cleanMobile,
      bloodGroup,
      gender: gender || 'Male',
      cityId: cityId || coordinator.cityId || null,
      verificationStatus: 'DONOR_VERIFIED',
      phoneVerified: true,
      emailVerified: false,
      isAvailable: true,
      role: 'DONOR'
    });

    await newUser.save();

    res.status(201).json({
      success: true,
      message: 'Walk-in donor enrolled successfully into live rescue pool.',
      user: sanitizeUser(newUser)
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// DELETE /api/users/account
router.delete('/account', authenticateToken, async (req, res) => {
  try {
    await User.findByIdAndDelete(req.user.id);
    res.json({ success: true, message: 'Account and records purged successfully' });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

module.exports = router;

