const express = require('express');
const router = express.Router();
const Hospital = require('../models/Hospital');
const City = require('../models/City');
const User = require('../models/User');
const { authenticateToken } = require('../middleware/auth');
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

// GET /api/hospitals/:id/doctors - List active medical officers for a hospital
router.get('/:id/doctors', async (req, res) => {
  try {
    const hospital = await Hospital.findById(req.params.id);
    if (!hospital) {
      return res.status(404).json({ success: false, message: 'Hospital not found' });
    }

    const activeDoctors = (hospital.doctors || [])
      .filter(d => d.isActive !== false)
      .map(d => ({
        id: d._id,
        name: d.name,
        designation: d.designation || 'Medical Officer',
        registrationNumber: d.registrationNumber,
        department: d.department || 'Blood Bank & Transfusion Unit',
        phone: d.phone || '',
        email: d.email || ''
      }));

    res.json({
      success: true,
      count: activeDoctors.length,
      doctors: activeDoctors
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// POST /api/hospitals/:id/doctors - Add a registered medical officer (Coordinator/Admin only)
router.post('/:id/doctors', authenticateToken, async (req, res) => {
  try {
    const { name, designation, registrationNumber, department, phone, email } = req.body;

    if (!name || !name.trim()) {
      return res.status(400).json({ success: false, message: 'Doctor name is required' });
    }
    if (!registrationNumber || !registrationNumber.trim()) {
      return res.status(400).json({ success: false, message: 'Medical Council Registration Number is required' });
    }

    const hospital = await Hospital.findById(req.params.id);
    if (!hospital) {
      return res.status(404).json({ success: false, message: 'Hospital not found' });
    }

    const caller = await User.findById(req.user.id);
    const isHospitalCoordinator = hospital.authorizedCoordinatorIds && hospital.authorizedCoordinatorIds.some(id => String(id) === req.user.id);
    const isGlobalAdmin = caller && caller.role === 'ADMIN';

    if (!isHospitalCoordinator && !isGlobalAdmin) {
      return res.status(403).json({ success: false, message: 'Forbidden: You are not an authorized coordinator for this hospital' });
    }

    if (!hospital.doctors) {
      hospital.doctors = [];
    }

    const newDoctor = {
      name: name.trim(),
      designation: (designation && designation.trim()) || 'Medical Officer (Transfusion Medicine)',
      registrationNumber: registrationNumber.trim().toUpperCase(),
      department: (department && department.trim()) || 'Blood Bank & Transfusion Unit',
      phone: phone || '',
      email: email || '',
      isActive: true,
      addedAt: new Date(),
      addedBy: req.user.id
    };

    hospital.doctors.push(newDoctor);
    await hospital.save();

    const savedDoc = hospital.doctors[hospital.doctors.length - 1];

    res.status(201).json({
      success: true,
      message: 'Medical officer added successfully to hospital roster',
      doctor: {
        id: savedDoc._id,
        name: savedDoc.name,
        designation: savedDoc.designation,
        registrationNumber: savedDoc.registrationNumber,
        department: savedDoc.department
      }
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// DELETE /api/hospitals/:id/doctors/:docId - Deactivate a medical officer
router.delete('/:id/doctors/:docId', authenticateToken, async (req, res) => {
  try {
    const hospital = await Hospital.findById(req.params.id);
    if (!hospital) {
      return res.status(404).json({ success: false, message: 'Hospital not found' });
    }

    const caller = await User.findById(req.user.id);
    const isHospitalCoordinator = hospital.authorizedCoordinatorIds && hospital.authorizedCoordinatorIds.some(id => String(id) === req.user.id);
    const isGlobalAdmin = caller && caller.role === 'ADMIN';

    if (!isHospitalCoordinator && !isGlobalAdmin) {
      return res.status(403).json({ success: false, message: 'Forbidden: You are not authorized to manage doctors for this hospital' });
    }

    const doctor = hospital.doctors.id(req.params.docId);
    if (!doctor) {
      return res.status(404).json({ success: false, message: 'Doctor not found in hospital roster' });
    }

    doctor.isActive = false;
    await hospital.save();

    res.json({
      success: true,
      message: 'Doctor removed from active hospital roster'
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

module.exports = router;
