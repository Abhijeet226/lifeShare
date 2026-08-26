const express = require('express');
const router = express.Router();
const mongoose = require('mongoose');
const City = require('../models/City');
const Hospital = require('../models/Hospital');

// GET /api/cities - List all active canonical cities
router.get('/', async (req, res) => {
  try {
    const { search, state } = req.query;
    const query = { isActive: true };

    if (state) {
      query.stateName = new RegExp(state.trim(), 'i');
    }
    if (search) {
      query.name = new RegExp(search.trim(), 'i');
    }

    const cities = await City.find(query).sort({ name: 1 });
    const formatted = cities.map((c) => ({
      id: c._id,
      cityId: c._id,
      name: c.name,
      stateName: c.stateName,
      stateCode: c.stateCode,
      countryName: c.countryName,
      countryCode: c.countryCode,
      location: c.location,
      latitude: c.location && c.location.coordinates ? c.location.coordinates[1] : null,
      longitude: c.location && c.location.coordinates ? c.location.coordinates[0] : null
    }));

    res.json({
      success: true,
      count: formatted.length,
      cities: formatted
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// GET /api/cities/:id - Get canonical city details
router.get('/:id', async (req, res) => {
  try {
    const { id } = req.params;
    let city = null;

    if (mongoose.Types.ObjectId.isValid(id)) {
      city = await City.findById(id);
    }
    if (!city) {
      city = await City.findOne({ normalizedName: id.trim().toLowerCase() });
    }

    if (!city) {
      return res.status(404).json({ success: false, message: 'City not found' });
    }

    res.json({
      success: true,
      city: {
        id: city._id,
        cityId: city._id,
        name: city.name,
        stateName: city.stateName,
        stateCode: city.stateCode,
        countryName: city.countryName,
        countryCode: city.countryCode,
        location: city.location,
        latitude: city.location && city.location.coordinates ? city.location.coordinates[1] : null,
        longitude: city.location && city.location.coordinates ? city.location.coordinates[0] : null,
        isActive: city.isActive
      }
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// GET /api/cities/:id/hospitals - Get verified hospitals in this city
router.get('/:id/hospitals', async (req, res) => {
  try {
    const { id } = req.params;
    let city = null;

    if (mongoose.Types.ObjectId.isValid(id)) {
      city = await City.findById(id);
    }
    if (!city) {
      city = await City.findOne({ normalizedName: id.trim().toLowerCase() });
    }

    if (!city) {
      return res.status(404).json({ success: false, message: 'City not found' });
    }

    const hospitals = await Hospital.find({ cityId: city._id }).sort({ name: 1 });

    const formatted = hospitals.map((h) => ({
      id: h._id,
      hospitalId: h._id,
      name: h.name,
      address: h.address,
      city: city.name,
      cityId: h.cityId || city._id,
      phone: h.phone,
      verified: !!h.verified
    }));

    res.json({
      success: true,
      city: city.name,
      count: formatted.length,
      hospitals: formatted
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

module.exports = router;
