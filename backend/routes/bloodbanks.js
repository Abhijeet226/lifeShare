const express = require('express');
const router = express.Router();
const BloodBank = require('../models/BloodBank');
const City = require('../models/City');

// GET /api/bloodbanks
router.get('/', async (req, res) => {
  try {
    const { city, cityId } = req.query;
    const query = {};
    if (cityId) {
      query.cityId = cityId;
    } else if (city && city !== 'All') {
      const matchingCities = await City.find({ name: new RegExp(city.trim(), 'i') }).select('_id');
      if (matchingCities.length > 0) {
        query.cityId = { $in: matchingCities.map(c => c._id) };
      }
    }

    const bloodBanks = await BloodBank.find(query)
      .populate('cityId', 'name stateName location')
      .sort({ name: 1 });

    const formatted = bloodBanks.map((b) => ({
      id: b._id,
      name: b.name,
      address: b.address,
      city: b.cityId && b.cityId.name ? b.cityId.name : 'Bhubaneswar',
      cityId: b.cityId ? (b.cityId._id || b.cityId) : null,
      contactNumber: b.contactNumber,
      availableUnits: b.availableUnits,
      timings: b.timings,
      type: b.type,
      location: b.location,
      verified: !!b.verified
    }));

    res.json({
      success: true,
      count: formatted.length,
      bloodBanks: formatted
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

module.exports = router;
