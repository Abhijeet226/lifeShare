const express = require('express');
const router = express.Router();
const User = require('../models/User');
const City = require('../models/City');
const { isValidCoordinate } = require('../services/locationService');
const { authenticateToken } = require('../middleware/auth');

// GET /api/donors/nearby - Protected Geospatial $geoNear query
router.get('/nearby', authenticateToken, async (req, res) => {
  try {
    const { bloodGroup, latitude, longitude, radius, isAvailable, limit = 20, page = 1 } = req.query;

    const lat = parseFloat(latitude);
    const lng = parseFloat(longitude);
    const radiusMeters = Math.min(parseInt(radius, 10) || 10000, 50000); // max 50km
    const maxLimit = Math.min(parseInt(limit, 10) || 20, 50);
    const skipCount = ((parseInt(page, 10) || 1) - 1) * maxLimit;

    if (!isValidCoordinate(lng, lat)) {
      return res.status(400).json({
        success: false,
        message: 'Valid latitude and longitude query parameters are required'
      });
    }

    const queryFilter = {
      accountStatus: 'ACTIVE'
    };

    // Exact blood group matching for standard donor search
    if (bloodGroup && bloodGroup !== 'All') {
      queryFilter.bloodGroup = bloodGroup.trim();
    }

    if (isAvailable !== 'false') {
      queryFilter.isAvailable = true;
    }

    const pipeline = [
      {
        $geoNear: {
          near: {
            type: 'Point',
            coordinates: [lng, lat]
          },
          distanceField: 'distanceMeters',
          maxDistance: radiusMeters,
          spherical: true,
          query: queryFilter
        }
      },
      {
        $lookup: {
          from: 'cities',
          localField: 'cityId',
          foreignField: '_id',
          as: 'cityDoc'
        }
      },
      {
        $unwind: {
          path: '$cityDoc',
          preserveNullAndEmptyArrays: true
        }
      },
      {
        $addFields: {
          verificationRank: {
            $cond: [
              { $eq: ['$verificationStatus', 'DONOR_VERIFIED'] },
              3,
              { $cond: [{ $eq: ['$verificationStatus', 'PHONE_VERIFIED'] }, 2, 1] }
            ]
          }
        }
      },
      {
        $sort: {
          verificationRank: -1,
          distanceMeters: 1,
          locationUpdatedAt: -1,
          lastActiveAt: -1
        }
      },
      {
        $project: {
          password: 0,
          location: 0 // Security requirement: Never expose raw coordinates
        }
      },
      { $skip: skipCount },
      { $limit: maxLimit }
    ];

    const results = await User.aggregate(pipeline);

    const formattedDonors = results.map((d) => {
      const distM = Math.round(d.distanceMeters || 0);
      const distKm = parseFloat((distM / 1000).toFixed(1));
      const isVerified = d.verificationStatus === 'DONOR_VERIFIED' || d.verificationStatus === 'PHONE_VERIFIED';

      return {
        id: d._id,
        name: d.name || `${d.firstName || ''} ${d.lastName || ''}`.trim(),
        firstName: d.firstName,
        lastName: d.lastName,
        bloodGroup: d.bloodGroup,
        city: d.cityDoc ? d.cityDoc.name : 'Bhubaneswar',
        cityId: d.cityId || (d.cityDoc ? d.cityDoc._id : null),
        mobile: d.hideMobileNumber ? '' : d.mobile,
        hideMobileNumber: !!d.hideMobileNumber,
        isAvailable: d.isAvailable,
        donorId: d.donorId || '',
        verificationStatus: d.verificationStatus || 'UNVERIFIED',
        verified: isVerified,
        distanceMeters: distM,
        distanceKm: distKm,
        lastActiveAt: d.lastActiveAt || d.updatedAt
      };
    });

    res.json({
      success: true,
      total: formattedDonors.length,
      radius: radiusMeters,
      donors: formattedDonors
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// GET /api/donors - Search by city/blood group
router.get('/', async (req, res) => {
  try {
    const { bloodGroup, city, cityId } = req.query;
    const query = { isAvailable: true, accountStatus: 'ACTIVE' };

    if (bloodGroup && bloodGroup !== 'All') {
      query.bloodGroup = bloodGroup.trim();
    }
    if (cityId) {
      query.cityId = cityId;
    } else if (city && city !== 'All' && city.trim() !== '') {
      const matchingCities = await City.find({ name: new RegExp(city.trim(), 'i') }).select('_id');
      if (matchingCities.length > 0) {
        query.cityId = { $in: matchingCities.map(c => c._id) };
      }
    }

    const donors = await User.find(query)
      .populate('cityId', 'name stateName location')
      .select('-password -location')
      .sort({ updatedAt: -1 })
      .limit(50);

    const sanitized = donors.map((d) => ({
      id: d._id,
      name: d.name,
      firstName: d.firstName,
      lastName: d.lastName,
      bloodGroup: d.bloodGroup,
      city: d.cityId && d.cityId.name ? d.cityId.name : 'Bhubaneswar',
      cityId: d.cityId ? (d.cityId._id || d.cityId) : null,
      mobile: d.hideMobileNumber ? '' : d.mobile,
      isAvailable: d.isAvailable,
      donorId: d.donorId,
      verificationStatus: d.verificationStatus || 'UNVERIFIED',
      verified: d.verificationStatus === 'DONOR_VERIFIED' || d.verificationStatus === 'PHONE_VERIFIED'
    }));

    res.json({
      success: true,
      count: sanitized.length,
      donors: sanitized
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

module.exports = router;
