const express = require('express');
const router = express.Router();
const Hospital = require('../models/Hospital');
const City = require('../models/City');
const { isValidCoordinate } = require('../services/locationService');

// GET /api/hospitals - List hospitals
router.get('/', async (req, res) => {
  try {
    const { city, cityId, search } = req.query;
    const query = {};

    if (cityId) {
      query.cityId = cityId;
    } else if (city && city !== 'All') {
      const matchingCities = await City.find({ name: new RegExp(city.trim(), 'i') }).select('_id');
      if (matchingCities.length > 0) {
        query.cityId = { $in: matchingCities.map(c => c._id) };
      }
    }
    if (search) {
      query.name = new RegExp(search.trim(), 'i');
    }

    const hospitals = await Hospital.find(query)
      .populate('cityId', 'name stateName location')
      .sort({ name: 1 });

    const formatted = hospitals.map((h) => {
      const cityName = h.cityId && h.cityId.name ? h.cityId.name : 'Bhubaneswar';
      return {
        id: h._id,
        hospitalId: h._id,
        name: h.name,
        address: h.address,
        city: cityName,
        cityId: h.cityId ? (h.cityId._id || h.cityId) : null,
        phone: h.phone,
        verified: !!h.verified
      };
    });

    res.json({
      success: true,
      count: formatted.length,
      hospitals: formatted
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// GET /api/hospitals/nearby - Geospatial hospital lookup
router.get('/nearby', async (req, res) => {
  try {
    const { latitude, longitude, radius = 25000 } = req.query;
    const lat = parseFloat(latitude);
    const lng = parseFloat(longitude);
    const radiusMeters = Math.min(parseInt(radius, 10) || 25000, 50000);

    if (!isValidCoordinate(lng, lat)) {
      return res.status(400).json({ success: false, message: 'Valid latitude and longitude required' });
    }

    const hospitals = await Hospital.aggregate([
      {
        $geoNear: {
          near: {
            type: 'Point',
            coordinates: [lng, lat]
          },
          distanceField: 'distanceMeters',
          maxDistance: radiusMeters,
          spherical: true
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
        $sort: { distanceMeters: 1 }
      },
      {
        $limit: 20
      }
    ]);

    const formatted = hospitals.map((h) => ({
      id: h._id,
      hospitalId: h._id,
      name: h.name,
      address: h.address,
      city: h.cityDoc ? h.cityDoc.name : 'Bhubaneswar',
      cityId: h.cityId || (h.cityDoc ? h.cityDoc._id : null),
      phone: h.phone,
      verified: !!h.verified,
      distanceKm: parseFloat(((h.distanceMeters || 0) / 1000).toFixed(1))
    }));

    res.json({
      success: true,
      count: formatted.length,
      hospitals: formatted
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

module.exports = router;
