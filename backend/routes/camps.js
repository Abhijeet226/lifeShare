const express = require('express');
const router = express.Router();
const mongoose = require('mongoose');
const BloodCamp = require('../models/BloodCamp');
const Hospital = require('../models/Hospital');
const City = require('../models/City');
const { authenticateToken } = require('../middleware/auth');

// GET /api/camps - List donation camps with status and city filtering
router.get('/', async (req, res) => {
  try {
    const { cityId, status } = req.query;
    const filter = {};

    if (status && ['UPCOMING', 'ONGOING', 'COMPLETED', 'CANCELLED'].includes(status.toUpperCase())) {
      filter.status = status.toUpperCase();
    } else {
      filter.status = { $in: ['UPCOMING', 'ONGOING'] };
    }

    if (cityId && mongoose.Types.ObjectId.isValid(cityId)) {
      filter.cityId = cityId;
    }

    // Optional user ID extracted if token present
    let currentUserId = null;
    const authHeader = req.headers.authorization;
    if (authHeader && authHeader.startsWith('Bearer ')) {
      try {
        const jwt = require('jsonwebtoken');
        const token = authHeader.split(' ')[1];
        const decoded = jwt.verify(token, process.env.JWT_SECRET || 'lifeshare_secure_jwt_secret_2026');
        currentUserId = decoded.id || decoded._id;
      } catch (e) {
        // Continue unauthenticated
      }
    }

    const camps = await BloodCamp.find(filter)
      .populate('hospitalId', 'name address phone')
      .populate('cityId', 'name stateName')
      .sort({ startDate: 1 })
      .lean();

    const formatted = camps.map(camp => {
      const isRsvped = currentUserId
        ? (camp.rsvps || []).some(r => r.userId && r.userId.toString() === currentUserId.toString())
        : false;

      return {
        id: camp._id,
        title: camp.title,
        organizerName: camp.organizerName,
        hospitalName: camp.hospitalId ? camp.hospitalId.name : null,
        hospitalId: camp.hospitalId ? camp.hospitalId._id : null,
        venueAddress: camp.venueAddress,
        cityName: camp.cityId ? camp.cityId.name : camp.cityName,
        cityId: camp.cityId ? camp.cityId._id : null,
        latitude: camp.location && camp.location.coordinates ? camp.location.coordinates[1] : 20.2961,
        longitude: camp.location && camp.location.coordinates ? camp.location.coordinates[0] : 85.8245,
        startDate: camp.startDate,
        endDate: camp.endDate,
        targetUnits: camp.targetUnits || 50,
        collectedUnits: camp.collectedUnits || 0,
        contactPhone: camp.contactPhone || '',
        status: camp.status,
        rsvpCount: (camp.rsvps || []).length,
        isUserRsvped: isRsvped,
        createdAt: camp.createdAt
      };
    });

    res.json({
      success: true,
      count: formatted.length,
      camps: formatted
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// POST /api/camps - Create a new donation drive / camp
router.post('/', authenticateToken, async (req, res) => {
  try {
    const {
      title,
      organizerName,
      hospitalId,
      venueAddress,
      cityId,
      cityName,
      latitude,
      longitude,
      startDate,
      endDate,
      targetUnits,
      contactPhone
    } = req.body;

    if (!title || !title.trim()) {
      return res.status(400).json({ success: false, message: 'Drive / Camp title is required.' });
    }
    if (!venueAddress || !venueAddress.trim()) {
      return res.status(400).json({ success: false, message: 'Venue address is required.' });
    }
    if (!startDate || !endDate) {
      return res.status(400).json({ success: false, message: 'Start date and end date are required.' });
    }

    let validHospitalId = null;
    let resolvedOrganizerName = organizerName ? organizerName.trim() : 'Hospital Blood Bank';

    if (hospitalId && mongoose.Types.ObjectId.isValid(hospitalId)) {
      const hospital = await Hospital.findById(hospitalId);
      if (hospital) {
        validHospitalId = hospital._id;
        resolvedOrganizerName = hospital.name;
      }
    }

    let validCityId = null;
    let resolvedCityName = cityName ? cityName.trim() : 'Bhubaneswar';
    if (cityId && mongoose.Types.ObjectId.isValid(cityId)) {
      const city = await City.findById(cityId);
      if (city) {
        validCityId = city._id;
        resolvedCityName = city.name;
      }
    }

    const lat = latitude ? parseFloat(latitude) : 20.2961;
    const lng = longitude ? parseFloat(longitude) : 85.8245;

    const camp = new BloodCamp({
      title: title.trim(),
      organizerName: resolvedOrganizerName,
      hospitalId: validHospitalId,
      venueAddress: venueAddress.trim(),
      cityId: validCityId,
      cityName: resolvedCityName,
      location: {
        type: 'Point',
        coordinates: [lng, lat]
      },
      startDate: new Date(startDate),
      endDate: new Date(endDate),
      targetUnits: parseInt(targetUnits, 10) || 50,
      contactPhone: contactPhone ? contactPhone.trim() : '',
      status: 'UPCOMING',
      rsvps: [],
      createdBy: req.user ? req.user.id : null
    });

    await camp.save();

    res.status(201).json({
      success: true,
      message: 'Blood donation camp scheduled successfully!',
      camp: {
        id: camp._id,
        title: camp.title,
        organizerName: camp.organizerName,
        venueAddress: camp.venueAddress,
        cityName: camp.cityName,
        startDate: camp.startDate,
        endDate: camp.endDate,
        targetUnits: camp.targetUnits,
        status: camp.status
      }
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// POST /api/camps/:id/rsvp - Toggle donor RSVP ("I'm Attending")
router.post('/:id/rsvp', authenticateToken, async (req, res) => {
  try {
    const { id } = req.params;
    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ success: false, message: 'Invalid Camp ID' });
    }

    const camp = await BloodCamp.findById(id);
    if (!camp) {
      return res.status(404).json({ success: false, message: 'Blood donation camp not found' });
    }

    const userId = req.user.id;
    const existingIndex = (camp.rsvps || []).findIndex(
      r => r.userId && r.userId.toString() === userId.toString()
    );

    let isRsvped = false;
    if (existingIndex >= 0) {
      // Un-RSVP
      camp.rsvps.splice(existingIndex, 1);
      isRsvped = false;
    } else {
      // Add RSVP
      camp.rsvps.push({ userId, joinedAt: new Date() });
      isRsvped = true;
    }

    await camp.save();

    res.json({
      success: true,
      isUserRsvped: isRsvped,
      rsvpCount: camp.rsvps.length,
      message: isRsvped ? 'You have RSVPed for this donation camp!' : 'RSVP cancelled.'
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

module.exports = router;
