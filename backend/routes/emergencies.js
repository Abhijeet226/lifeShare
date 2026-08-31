const express = require('express');
const router = express.Router();
const mongoose = require('mongoose');
const EmergencyRequest = require('../models/EmergencyRequest');
const EmergencyResponse = require('../models/EmergencyResponse');
const DonationHistory = require('../models/DonationHistory');
const Hospital = require('../models/Hospital');
const User = require('../models/User');
const City = require('../models/City');
const { authenticateToken, optionalToken } = require('../middleware/auth');
const { findMatchingDonors } = require('../services/donorMatching');
const notificationService = require('../services/notificationService');
const { isValidCoordinate } = require('../services/locationService');
const { checkDonorEligibility } = require('../services/cooldownService');
const { generateDonationCertificate } = require('../services/certificateService');
const { logAuditEvent } = require('../services/auditService');

// Centralized User-Level Status Display Formatter
const getEmergencyStatusDisplay = (status, unitsFulfilled = 0, unitsRequired = 1) => {
  if (status === 'FULFILLED' || (unitsRequired > 0 && unitsFulfilled >= unitsRequired)) {
    return 'Fulfilled';
  }
  if (status === 'PARTIAL' || unitsFulfilled > 0) {
    return 'Partially Fulfilled';
  }
  if (status === 'EXPIRED') return 'Expired';
  if (status === 'CANCELLED') return 'Closed';
  return 'Seeking Donors';
};

const getJourneyStatusDisplay = (status) => {
  switch (status) {
    case 'ACCEPTED':
      return 'On The Way';
    case 'TRAVELLING':
      return 'Travelling';
    case 'ARRIVED':
      return 'At Hospital Desk';
    case 'DONATED':
    case 'COMPLETED':
      return 'Donation Verified';
    case 'CANCELLED':
    case 'DECLINED':
      return 'Cancelled';
    default:
      return status || 'Seeking Donors';
  }
};

// POST /api/emergency/create & POST /api/emergency
const handleCreateEmergency = async (req, res) => {
  try {
    const {
      patientName,
      hospital,
      hospitalId,
      city,
      bloodGroup,
      unitsRequired,
      unitsNeeded,
      contactNumber,
      urgency,
      latitude,
      longitude,
      hospitalLatitude,
      hospitalLongitude,
      postedBy
    } = req.body;

    if (!patientName || (!hospital && !hospitalId) || !bloodGroup || !contactNumber) {
      return res.status(400).json({
        success: false,
        message: 'Patient name, hospital, blood group, and contact number are required'
      });
    }

    const units = Math.max(1, parseInt(unitsRequired || unitsNeeded || 1, 10));
    const requesterId = req.user ? req.user.id : null;
    const requesterEmail = req.user ? req.user.email : (postedBy || '');

    const requestData = {
      requester: requesterId,
      patientName: patientName.trim(),
      city: city || 'Bhubaneswar',
      bloodGroup: bloodGroup.trim(),
      unitsRequired: units,
      unitsNeeded: units,
      acceptedCount: 0,
      unitsFulfilled: 0,
      contactNumber: contactNumber.trim(),
      postedBy: requesterEmail,
      urgency: urgency || 'URGENT',
      status: 'SEARCHING',
      isFulfilled: false
    };

    // Authoritative Hospital Lookup
    if (hospitalId && mongoose.Types.ObjectId.isValid(hospitalId)) {
      const verifiedHospital = await Hospital.findById(hospitalId);
      if (verifiedHospital) {
        requestData.hospitalId = verifiedHospital._id;
        requestData.hospital = verifiedHospital.name;
        requestData.hospitalName = verifiedHospital.name;
        requestData.hospitalAddress = verifiedHospital.address;
        if (verifiedHospital.cityId) {
          requestData.cityId = verifiedHospital.cityId;
        }
        requestData.isAuthoritativeHospital = true;
        if (verifiedHospital.location && verifiedHospital.location.coordinates) {
          requestData.hospitalLocation = {
            type: 'Point',
            coordinates: verifiedHospital.location.coordinates
          };
        }
      }
    }

    // Auto-resolve cityId if not set yet
    if (!requestData.cityId && (cityId || city)) {
      if (cityId && mongoose.Types.ObjectId.isValid(cityId)) {
        requestData.cityId = cityId;
      } else if (city) {
        const cityDoc = await City.findOne({ normalizedName: city.trim().toLowerCase() });
        if (cityDoc) {
          requestData.cityId = cityDoc._id;
        }
      }
    }

    // Auto-resolve coordinator assigned hospital if requester is coordinator
    if (!requestData.hospitalId && req.user && req.user.role === 'COORDINATOR') {
      try {
        const coordUser = await User.findById(req.user.id);
        if (coordUser && coordUser.hospitalId) {
          requestData.hospitalId = coordUser.hospitalId;
          const hDoc = await Hospital.findById(coordUser.hospitalId);
          if (hDoc) {
            requestData.hospital = hDoc.name;
            requestData.hospitalName = hDoc.name;
            requestData.hospitalAddress = hDoc.address;
            requestData.isAuthoritativeHospital = true;
            if (hDoc.location && hDoc.location.coordinates) {
              requestData.hospitalLocation = {
                type: 'Point',
                coordinates: hDoc.location.coordinates
              };
            }
          }
        }
      } catch (e) {
        console.error('Coordinator hospital lookup error:', e);
      }
    }

    requestData.isHospitalVerified = !!(req.body.isHospitalVerified || (req.user && req.user.role === 'COORDINATOR'));

    // Manual/Free-text fallback if hospitalId wasn't found or provided
    if (!requestData.hospital) {
      requestData.hospital = (hospital || 'Hospital').trim();
      requestData.hospitalName = requestData.hospital;
      requestData.isAuthoritativeHospital = false;
    }

    // Optional manual hospital coordinates
    if (!requestData.hospitalLocation && isValidCoordinate(hospitalLongitude, hospitalLatitude)) {
      requestData.hospitalLocation = {
        type: 'Point',
        coordinates: [hospitalLongitude, hospitalLatitude]
      };
    }

    // Requester GPS Coordinates
    if (isValidCoordinate(longitude, latitude)) {
      requestData.requestLocation = {
        type: 'Point',
        coordinates: [longitude, latitude] // GeoJSON: [lng, lat]
      };
    }

    const emergency = new EmergencyRequest(requestData);
    await emergency.save();

    // Populate canonical city metadata before responding
    await emergency.populate('cityId', 'name stateName location');

    // Safe Audit Logging
    logAuditEvent({
      actorId: requesterId,
      actorRole: req.user ? (req.user.role || 'DONOR') : 'ANONYMOUS',
      action: 'EMERGENCY_CREATED',
      entityType: 'EmergencyRequest',
      entityId: emergency._id,
      metadata: {
        bloodGroup: emergency.bloodGroup,
        unitsRequired: emergency.unitsRequired,
        hospital: emergency.hospital,
        cityId: emergency.cityId ? (emergency.cityId._id || emergency.cityId) : null
      }
    });

    // Trigger progressive background donor matching
    findMatchingDonors(emergency._id, 0).catch((e) =>
      console.error('Error triggering matching engine:', e.message)
    );

    res.status(201).json({
      success: true,
      request: emergency
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
};

router.post('/create', optionalToken, handleCreateEmergency);
router.post('/', optionalToken, handleCreateEmergency);

// GET /api/emergency/list
router.get('/list', async (req, res) => {
  try {
    const requests = await EmergencyRequest.find({
      status: { $nin: ['CANCELLED', 'EXPIRED', 'COMPLETED', 'DONATION_COMPLETED'] },
      isActive: { $ne: false }
    })
      .populate('requester', 'accountStatus isDeleted')
      .populate('cityId', 'name stateName location')
      .sort({ createdAt: -1 });

    // Exclude any requests created by suspended, blocked, or deleted users, or flagged as fraud
    const activeRequests = requests.filter((r) => {
      if (r.requester) {
        if (['SUSPENDED', 'BLOCKED'].includes(r.requester.accountStatus) || r.requester.isDeleted) {
          return false;
        }
      }
      if (r.patientName && /fraud/i.test(r.patientName)) {
        return false;
      }
      return true;
    });

    const formattedRequests = activeRequests.map((r) => {
      const obj = r.toObject ? r.toObject() : { ...r };
      obj.statusDisplay = getEmergencyStatusDisplay(r.status, r.unitsFulfilled || 0, r.unitsRequired || 1);
      return obj;
    });

    res.json({
      success: true,
      count: formattedRequests.length,
      requests: formattedRequests
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// GET /api/emergency/:id
// =========================================================================
// PHASE 2: STRICT DONOR JOURNEY STATE MACHINE
// =========================================================================
const VALID_TRANSITIONS = {
  'NOTIFIED': ['VIEWED', 'ACCEPTED', 'DECLINED'],
  'VIEWED': ['ACCEPTED', 'DECLINED'],
  'ACCEPTED': ['TRAVELLING', 'CANCELLED'],
  'TRAVELLING': ['ARRIVED', 'CANCELLED'],
  'ARRIVED': [], // Terminal state for donor. Pending authorized medical verification.
  'DONATED': ['COMPLETED'], // Authorized/coordinator flow
  'COMPLETED': [],
  'DECLINED': [],
  'CANCELLED': []
};

// GET /api/emergencies/:id & GET /api/emergency/:id
router.get('/:id', optionalToken, async (req, res) => {
  try {
    const { id } = req.params;
    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ success: false, message: 'Invalid Emergency ID' });
    }

    const emergency = await EmergencyRequest.findById(id).populate('cityId', 'name stateName location');
    if (!emergency) {
      return res.status(404).json({ success: false, message: 'Emergency request not found' });
    }

    // Fetch all responses with populated donor public fields
    const responses = await EmergencyResponse.find({ requestId: emergency._id })
      .populate('donorId', 'name bloodGroup verificationStatus accountStatus donorId');

    const notifiedCount = responses.length;
    const acceptedResponses = responses.filter((r) =>
      ['ACCEPTED', 'TRAVELLING', 'ARRIVED', 'DONATED', 'COMPLETED'].includes(r.status)
    );
    const acceptedCount = acceptedResponses.length;
    const requiredUnits = emergency.unitsRequired || emergency.unitsNeeded || 1;
    const remainingUnits = Math.max(0, requiredUnits - acceptedCount);

    let myJourney = null;
    let isRequester = false;

    if (req.user && req.user.id) {
      isRequester = emergency.requester && emergency.requester.toString() === req.user.id;

      const myResp = responses.find((r) => {
        const dId = r.donorId ? (r.donorId._id || r.donorId).toString() : null;
        return dId === req.user.id;
      });

      if (myResp) {
        myJourney = {
          responseId: myResp._id,
          status: myResp.status,
          statusDisplay: getJourneyStatusDisplay(myResp.status),
          acceptedAt: myResp.acceptedAt,
          travellingAt: myResp.travellingAt,
          arrivedAt: myResp.arrivedAt,
          donatedAt: myResp.donatedAt,
          completedAt: myResp.completedAt,
          isAccepted: ['ACCEPTED', 'TRAVELLING', 'ARRIVED', 'DONATED', 'COMPLETED'].includes(myResp.status),
          canStartJourney: myResp.status === 'ACCEPTED',
          canMarkArrived: myResp.status === 'TRAVELLING',
          isPendingVerification: myResp.status === 'ARRIVED',
          isCompleted: ['DONATED', 'COMPLETED'].includes(myResp.status)
        };
      }
    }

    // Format accepted donors list for requester view (sanitized, privacy-safe, no raw home GPS)
    const acceptedDonors = acceptedResponses.map((r) => {
      const d = r.donorId || {};
      return {
        donorId: d._id || r.donorId,
        name: d.name || 'Voluntary Donor',
        bloodGroup: d.bloodGroup || emergency.bloodGroup,
        verificationStatus: d.verificationStatus || 'UNVERIFIED',
        journeyStatus: r.status,
        journeyStatusDisplay: getJourneyStatusDisplay(r.status),
        acceptedAt: r.acceptedAt,
        travellingAt: r.travellingAt,
        arrivedAt: r.arrivedAt,
        donatedAt: r.donatedAt
      };
    });

    const emergencyObj = emergency.toObject ? emergency.toObject() : { ...emergency };
    emergencyObj.statusDisplay = getEmergencyStatusDisplay(emergency.status, emergency.unitsFulfilled || 0, requiredUnits);

    res.json({
      success: true,
      emergency: emergencyObj,
      stats: {
        unitsRequired: requiredUnits,
        acceptedCount,
        remainingUnits,
        notifiedCount,
        isFulfilled: emergency.isFulfilled || acceptedCount >= requiredUnits
      },
      isRequester,
      myJourney,
      userResponseStatus: myJourney ? myJourney.status : null,
      acceptedDonors: isRequester ? acceptedDonors : undefined
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// POST /api/emergencies/:id/respond & POST /api/emergencies/:id/journey
const handleEmergencyJourneyAction = async (req, res) => {
  try {
    const { id } = req.params;
    const action = (req.body.action || req.body.response || '').trim().toUpperCase();

    const ALL_ACTIONS = ['VIEWED', 'ACCEPTED', 'DECLINED', 'TRAVELLING', 'ARRIVED', 'CANCELLED', 'DONATED', 'COMPLETED'];
    if (!ALL_ACTIONS.includes(action)) {
      return res.status(400).json({
        success: false,
        message: `Invalid journey action: "${action}". Must be one of: ${ALL_ACTIONS.join(', ')}.`
      });
    }

    // Security: Donor cannot self-certify medical donation completion
    if (action === 'DONATED' || action === 'COMPLETED') {
      return res.status(403).json({
        success: false,
        message: 'Donation completion must be certified by authorized medical coordinator/requester.'
      });
    }

    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ success: false, message: 'Invalid Emergency ID' });
    }

    const emergency = await EmergencyRequest.findById(id);
    if (!emergency) {
      return res.status(404).json({ success: false, message: 'Emergency request not found' });
    }

    // Reject journey actions on inactive emergencies
    if (['CANCELLED', 'EXPIRED', 'COMPLETED', 'DONATION_COMPLETED'].includes(emergency.status)) {
      return res.status(400).json({
        success: false,
        message: `This emergency request is no longer active (status: ${emergency.status}).`
      });
    }

    // Security: donorId derived strictly from verified JWT
    const donorId = req.user.id;
    const donor = await User.findById(donorId);
    if (!donor) {
      return res.status(404).json({ success: false, message: 'Donor account not found' });
    }

    if (donor.accountStatus === 'SUSPENDED' || donor.accountStatus === 'BLOCKED') {
      return res.status(403).json({
        success: false,
        message: `Account is ${donor.accountStatus.toLowerCase()}. Cannot respond to emergencies.`
      });
    }

    let record = await EmergencyResponse.findOne({ requestId: emergency._id, donorId });
    const requiredUnits = emergency.unitsRequired || emergency.unitsNeeded || 1;

    // If no prior record exists
    if (!record) {
      if (action === 'VIEWED') {
        record = new EmergencyResponse({
          requestId: emergency._id,
          donorId,
          status: 'VIEWED',
          viewedAt: new Date()
        });
        await record.save();
        return res.json({
          success: true,
          message: 'Emergency request marked as viewed.',
          responseStatus: record.status,
          myJourney: { status: record.status, viewedAt: record.viewedAt }
        });
      } else if (action === 'ACCEPTED' || action === 'DECLINED') {
        // Will be created during ACCEPT / DECLINE below with initial NOTIFIED state assumption
        record = new EmergencyResponse({
          requestId: emergency._id,
          donorId,
          status: 'NOTIFIED',
          notifiedAt: new Date()
        });
      } else {
        return res.status(400).json({
          success: false,
          message: `Cannot transition to ${action} without receiving notification or accepting request first.`
        });
      }
    }

    const currentStatus = record.status || 'NOTIFIED';

    // 1. Idempotency check: Already in requested state
    if (currentStatus === action) {
      return res.json({
        success: true,
        message: `Journey status is already ${action}.`,
        responseStatus: record.status,
        myJourney: {
          status: record.status,
          viewedAt: record.viewedAt,
          acceptedAt: record.acceptedAt,
          travellingAt: record.travellingAt,
          arrivedAt: record.arrivedAt
        }
      });
    }

    // 2. State machine transition validation
    const allowedTransitions = VALID_TRANSITIONS[currentStatus] || [];
    if (!allowedTransitions.includes(action)) {
      return res.status(400).json({
        success: false,
        message: `Invalid journey transition from ${currentStatus} to ${action}.`
      });
    }

    // 3. Handle ACCEPTED (with multi-unit capacity & 90-day cooldown constraint)
    if (action === 'ACCEPTED') {
      // Enforce 90-day post-donation cooldown
      const eligibility = checkDonorEligibility(donor);
      if (!eligibility.isEligible) {
        return res.status(403).json({
          success: false,
          message: `Donor is currently within the post-donation eligibility cooldown period (${eligibility.daysRemaining} days remaining).`,
          eligibility
        });
      }

      const currentAccepted = await EmergencyResponse.countDocuments({
        requestId: emergency._id,
        status: { $in: ['ACCEPTED', 'TRAVELLING', 'ARRIVED', 'DONATED', 'COMPLETED'] }
      });

      if (currentAccepted >= requiredUnits) {
        return res.status(409).json({
          success: false,
          message: 'All required blood units for this emergency have already been accepted by other donors.',
          isFulfilled: true
        });
      }

      record.status = 'ACCEPTED';
      if (!record.acceptedAt) record.acceptedAt = new Date();
      if (!record.respondedAt) record.respondedAt = new Date();
      await record.save();

      const newAcceptedCount = currentAccepted + 1;
      emergency.acceptedCount = newAcceptedCount;
      if (newAcceptedCount >= requiredUnits) {
        emergency.status = 'ACCEPTED';
      } else {
        emergency.status = 'PARTIALLY_ACCEPTED';
      }
      // isFulfilled is only true once all required donations are medically verified
      if (!emergency.unitsFulfilled || emergency.unitsFulfilled < requiredUnits) {
        emergency.isFulfilled = false;
      }
      await emergency.save();

      // Notify requester
      if (emergency.requester) {
        notificationService.notifyRequesterOfAcceptance(emergency.requester, emergency, donor);
      }

      return res.json({
        success: true,
        message: 'Emergency request successfully accepted.',
        responseStatus: record.status,
        acceptedCount: newAcceptedCount,
        unitsRequired: requiredUnits,
        myJourney: {
          status: record.status,
          acceptedAt: record.acceptedAt,
          canStartJourney: true
        }
      });
    }

    // 4. Handle TRAVELLING
    if (action === 'TRAVELLING') {
      record.status = 'TRAVELLING';
      if (!record.travellingAt) record.travellingAt = new Date();
      await record.save();

      return res.json({
        success: true,
        message: 'Journey status updated to TRAVELLING.',
        responseStatus: record.status,
        myJourney: {
          status: record.status,
          acceptedAt: record.acceptedAt,
          travellingAt: record.travellingAt,
          canMarkArrived: true
        }
      });
    }

    // 5. Handle ARRIVED
    if (action === 'ARRIVED') {
      record.status = 'ARRIVED';
      if (!record.arrivedAt) record.arrivedAt = new Date();
      await record.save();

      // Notify Assigned Hospital Coordinators
      try {
        let coordinatorQuery = { role: 'COORDINATOR', accountStatus: 'ACTIVE' };
        if (emergency.hospitalId && mongoose.Types.ObjectId.isValid(emergency.hospitalId)) {
          coordinatorQuery.hospitalId = emergency.hospitalId;
        }
        let coordinators = await User.find(coordinatorQuery);
        if (!coordinators || coordinators.length === 0) {
          coordinators = await User.find({ role: 'COORDINATOR', accountStatus: 'ACTIVE' });
        }

        for (const coord of coordinators) {
          notificationService.sendToUser(coord._id, {
            title: `🩸 Donor Arrived: ${donor.name || 'Donor'} (${donor.bloodGroup || emergency.bloodGroup})`,
            body: `${donor.name || 'A donor'} has arrived at ${emergency.hospital} for patient ${emergency.patientName}. Tap to verify donation.`,
            data: {
              notificationType: 'DONOR_ARRIVED',
              requestId: String(emergency._id),
              responseId: String(record._id),
              donorId: String(donor._id),
              donorName: donor.name || 'Donor',
              bloodGroup: donor.bloodGroup || emergency.bloodGroup,
              patientName: emergency.patientName,
              hospital: emergency.hospital,
              targetScreen: 'COORDINATOR_VERIFICATION'
            },
            notificationType: 'DONOR_ARRIVED'
          });
        }
      } catch (notifyErr) {
        console.warn('⚠️ Could not notify coordinators on donor arrival:', notifyErr.message);
      }

      return res.json({
        success: true,
        message: 'Arrival at hospital confirmed. Awaiting authorized donation verification.',
        responseStatus: record.status,
        myJourney: {
          status: record.status,
          acceptedAt: record.acceptedAt,
          travellingAt: record.travellingAt,
          arrivedAt: record.arrivedAt,
          isPendingVerification: true
        }
      });
    }

    // 6. Handle CANCELLED by Donor
    if (action === 'CANCELLED') {
      const wasAccepted = ['ACCEPTED', 'TRAVELLING'].includes(currentStatus);
      record.status = 'CANCELLED';
      await record.save();

      if (wasAccepted) {
        const remainingAccepted = await EmergencyResponse.countDocuments({
          requestId: emergency._id,
          status: { $in: ['ACCEPTED', 'TRAVELLING', 'ARRIVED', 'DONATED', 'COMPLETED'] }
        });
        emergency.acceptedCount = remainingAccepted;
        emergency.isFulfilled = remainingAccepted >= requiredUnits;
        if (emergency.isFulfilled) {
          emergency.status = 'FULFILLED';
        } else if (remainingAccepted > 0) {
          emergency.status = 'PARTIALLY_ACCEPTED';
        } else {
          emergency.status = 'SEARCHING';
        }
        await emergency.save();
      }

      return res.json({
        success: true,
        message: 'Your response has been cancelled.',
        responseStatus: record.status
      });
    }

    // 7. Handle DECLINED / VIEWED
    record.status = action;
    if (action === 'VIEWED' && !record.viewedAt) record.viewedAt = new Date();
    if (action === 'DECLINED' && !record.respondedAt) record.respondedAt = new Date();
    await record.save();

    res.json({
      success: true,
      message: `Status updated to ${action}.`,
      responseStatus: record.status
    });
  } catch (err) {
    console.error('Error handling emergency journey action:', err);
    res.status(500).json({ success: false, message: err.message });
  }
};

router.post('/:id/respond', authenticateToken, handleEmergencyJourneyAction);
router.post('/:id/journey', authenticateToken, handleEmergencyJourneyAction);

// =========================================================================
// PHASE 3: AUTHORIZED HOSPITAL COORDINATOR DONATION VERIFICATION
// =========================================================================

// GET /api/emergencies/coordinator/pending-verifications - Coordinator Dashboard
router.get('/coordinator/pending-verifications', authenticateToken, async (req, res) => {
  try {
    const caller = await User.findById(req.user.id);
    if (!caller || (caller.role !== 'COORDINATOR' && caller.role !== 'ADMIN')) {
      return res.status(403).json({
        success: false,
        message: 'Forbidden: Access restricted to authorized Hospital Coordinators and Admins.'
      });
    }

    if (caller.accountStatus === 'BLOCKED' || caller.accountStatus === 'SUSPENDED') {
      return res.status(403).json({ success: false, message: 'Account is suspended or blocked.' });
    }

    let emergencyQuery = { status: { $nin: ['CANCELLED', 'EXPIRED'] } };
    if (caller.role === 'COORDINATOR' && caller.hospitalId) {
      emergencyQuery.hospitalId = caller.hospitalId;
    }

    const emergencies = await EmergencyRequest.find(emergencyQuery);
    const emergencyIds = emergencies.map((e) => e._id);

    const pendingResponses = await EmergencyResponse.find({
      requestId: { $in: emergencyIds },
      status: { $in: ['ARRIVED', 'ACCEPTED'] }
    })
      .populate('donorId', 'name bloodGroup mobile email donorId verificationStatus')
      .populate('requestId', 'patientName hospital hospitalAddress bloodGroup unitsRequired unitsFulfilled status')
      .sort({ arrivedAt: -1, createdAt: -1 });

    const items = pendingResponses.map((r) => ({
      responseId: r._id,
      requestId: r.requestId ? r.requestId._id : null,
      patientName: r.requestId ? r.requestId.patientName : 'Patient',
      hospital: r.requestId ? r.requestId.hospital : 'Hospital',
      bloodGroup: r.requestId ? r.requestId.bloodGroup : 'O+',
      unitsRequired: r.requestId ? (r.requestId.unitsRequired || 1) : 1,
      unitsFulfilled: r.requestId ? (r.requestId.unitsFulfilled || 0) : 0,
      donorId: r.donorId ? r.donorId._id : null,
      donorName: r.donorId ? r.donorId.name : 'Voluntary Donor',
      donorMobile: r.donorId ? r.donorId.mobile : '',
      donorBloodGroup: r.donorId ? r.donorId.bloodGroup : 'O+',
      donorVerificationStatus: r.donorId ? r.donorId.verificationStatus : 'UNVERIFIED',
      arrivedAt: r.arrivedAt,
      createdAt: r.createdAt,
      status: r.status // 'ARRIVED' or 'ACCEPTED'
    }));

    res.json({
      success: true,
      count: items.length,
      pendingVerifications: items
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// GET /api/emergencies/coordinator/hospital-emergencies
router.get('/coordinator/hospital-emergencies', authenticateToken, async (req, res) => {
  try {
    const caller = await User.findById(req.user.id);
    if (!caller || (caller.role !== 'COORDINATOR' && caller.role !== 'ADMIN')) {
      return res.status(403).json({ success: false, message: 'Access restricted to authorized coordinators/admins.' });
    }

    let query = { status: { $nin: ['CANCELLED', 'EXPIRED'] } };
    if (caller.role === 'COORDINATOR' && caller.hospitalId) {
      query.hospitalId = caller.hospitalId;
    }

    const emergencies = await EmergencyRequest.find(query)
      .populate('hospitalId', 'name address phone city')
      .populate('cityId', 'name stateName')
      .sort({ createdAt: -1 })
      .limit(50);

    res.json({
      success: true,
      count: emergencies.length,
      emergencies: emergencies.map((e) => ({
        id: e._id,
        patientName: e.patientName,
        bloodGroup: e.bloodGroup,
        unitsRequired: e.unitsRequired,
        unitsFulfilled: e.unitsFulfilled,
        urgency: e.urgency,
        hospital: e.hospital,
        hospitalAddress: e.hospitalAddress,
        contactNumber: e.contactNumber,
        status: e.status,
        createdAt: e.createdAt
      }))
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// GET /api/emergencies/coordinator/history
router.get('/coordinator/history', authenticateToken, async (req, res) => {
  try {
    const caller = await User.findById(req.user.id);
    if (!caller || (caller.role !== 'COORDINATOR' && caller.role !== 'ADMIN')) {
      return res.status(403).json({ success: false, message: 'Access restricted to authorized coordinators/admins.' });
    }

    const DonationHistory = require('../models/DonationHistory');
    let query = { status: 'COMPLETED' };
    if (caller.role === 'COORDINATOR' && caller.hospitalId) {
      query.hospitalId = caller.hospitalId;
    }

    const history = await DonationHistory.find(query)
      .populate('donorId', 'name bloodGroup mobile donorId')
      .populate('verifiedBy', 'name email role')
      .sort({ verifiedAt: -1, donationDate: -1 })
      .limit(50);

    res.json({
      success: true,
      count: history.length,
      history: history.map((h) => ({
        id: h._id,
        certificateId: h.certificateId,
        donorName: h.donorId ? h.donorId.name : 'Voluntary Donor',
        donorBloodGroup: h.donorId ? h.donorId.bloodGroup : h.bloodGroup,
        hospital: h.hospitalName || 'Hospital',
        unitsDonated: h.unitsDonated || 1,
        verifiedAt: h.verifiedAt || h.donationDate,
        status: h.status
      }))
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

// POST /api/emergencies/:id/verify-donation
router.post('/:id/verify-donation', authenticateToken, async (req, res) => {
  try {
    const { id } = req.params;
    const { donorId, doctorName, doctorRegistrationNo, unitsDonated } = req.body;

    if (!donorId || !mongoose.Types.ObjectId.isValid(donorId)) {
      return res.status(400).json({ success: false, message: 'Valid donorId is required for donation verification.' });
    }

    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ success: false, message: 'Invalid Emergency ID' });
    }

    const emergency = await EmergencyRequest.findById(id);
    if (!emergency) {
      return res.status(404).json({ success: false, message: 'Emergency request not found' });
    }

    if (['CANCELLED', 'EXPIRED'].includes(emergency.status)) {
      return res.status(400).json({
        success: false,
        message: `Cannot verify donation for an inactive emergency (status: ${emergency.status}).`
      });
    }

    // 1. Authorization: Caller MUST be COORDINATOR or ADMIN
    const verifier = await User.findById(req.user.id);
    if (!verifier) {
      return res.status(404).json({ success: false, message: 'Verifier account not found' });
    }

    if (verifier.accountStatus === 'BLOCKED' || verifier.accountStatus === 'SUSPENDED') {
      return res.status(403).json({
        success: false,
        message: 'Verifier account is suspended or blocked.'
      });
    }

    const isPrivileged = verifier.role === 'ADMIN';
    let isAuthorizedCoordinator = false;

    if (verifier.role === 'COORDINATOR') {
      // Check 1: Direct hospitalId match
      if (verifier.hospitalId && emergency.hospitalId && String(verifier.hospitalId) === String(emergency.hospitalId)) {
        isAuthorizedCoordinator = true;
      }
      // Check 2: Hospital document includes verifier in authorizedCoordinatorIds
      if (!isAuthorizedCoordinator && emergency.hospitalId) {
        const hosp = await Hospital.findById(emergency.hospitalId);
        if (hosp && hosp.authorizedCoordinatorIds && hosp.authorizedCoordinatorIds.some((cid) => String(cid) === String(verifier._id))) {
          isAuthorizedCoordinator = true;
        }
      }
      // Check 3: Match by hospital name fallback
      if (!isAuthorizedCoordinator && !emergency.hospitalId && verifier.hospitalId) {
        const hosp = await Hospital.findById(verifier.hospitalId);
        if (hosp && hosp.name.toLowerCase() === (emergency.hospital || '').toLowerCase()) {
          isAuthorizedCoordinator = true;
        }
      }
    }

    if (!isPrivileged && !isAuthorizedCoordinator) {
      return res.status(403).json({
        success: false,
        message: 'Forbidden: You are not authorized to verify donations for this hospital.'
      });
    }

    // 2. Check Donor Account
    const donor = await User.findById(donorId);
    if (!donor) {
      return res.status(404).json({ success: false, message: 'Donor account not found' });
    }

    if (donor.accountStatus === 'BLOCKED') {
      return res.status(403).json({
        success: false,
        message: 'Donor account is blocked. Donation cannot be verified.'
      });
    }

    // 3. Find EmergencyResponse record
    const responseRecord = await EmergencyResponse.findOne({ requestId: emergency._id, donorId: donor._id });
    if (!responseRecord) {
      return res.status(404).json({
        success: false,
        message: 'No response record found for this donor on the specified emergency.'
      });
    }

    // 4. Idempotency Check: If already DONATED
    if (responseRecord.status === 'DONATED' || responseRecord.status === 'COMPLETED') {
      const existingDonation = await DonationHistory.findOne({ requestId: emergency._id, donorId: donor._id });
      return res.json({
        success: true,
        message: 'Donation has already been verified.',
        isAlreadyVerified: true,
        donation: existingDonation,
        certificateId: existingDonation ? existingDonation.certificateId : null
      });
    }

    // 5. State Machine Validation: Donor MUST have reached ARRIVED
    if (responseRecord.status !== 'ARRIVED') {
      return res.status(400).json({
        success: false,
        message: `Donor must reach ARRIVED state before verification (current status: ${responseRecord.status}).`
      });
    }

    // 6. Generate Cryptographic Certificate
    const donationDate = new Date();
    const cleanDoctorName = (doctorName && doctorName.trim()) || 'Attending Medical Officer';
    const cleanDoctorRegNo = (doctorRegistrationNo && doctorRegistrationNo.trim().toUpperCase()) || '';

    const certificateData = generateDonationCertificate({
      donorId: donor._id,
      bloodGroup: donor.bloodGroup || emergency.bloodGroup,
      hospitalName: emergency.hospital,
      donationDate,
      verifiedById: verifier._id,
      attendingDoctor: cleanDoctorName,
      doctorRegistrationNo: cleanDoctorRegNo
    });

    // 7. Atomic Insert into DonationHistory (Compound unique index guarantees no double-insert)
    let donationHistory;
    try {
      donationHistory = new DonationHistory({
        donorId: donor._id,
        requestId: emergency._id,
        hospitalId: emergency.hospitalId || null,
        hospital: emergency.hospital,
        patientName: emergency.patientName,
        bloodGroup: donor.bloodGroup || emergency.bloodGroup,
        unitsDonated: parseInt(unitsDonated, 10) || 1,
        donationDate,
        status: 'VERIFIED',
        attendingDoctor: cleanDoctorName,
        doctorRegistrationNo: cleanDoctorRegNo,
        verifiedBy: verifier._id,
        verifiedAt: donationDate,
        certificateId: certificateData.certificateId,
        certificateHash: certificateData.certificateHash
      });
      await donationHistory.save();
    } catch (dupErr) {
      if (dupErr.code === 11000) {
        const existing = await DonationHistory.findOne({ requestId: emergency._id, donorId: donor._id });
        return res.json({
          success: true,
          message: 'Donation has already been verified.',
          isAlreadyVerified: true,
          donation: existing,
          certificateId: existing ? existing.certificateId : null
        });
      }
      throw dupErr;
    }

    // 8. Update EmergencyResponse to DONATED
    responseRecord.status = 'DONATED';
    responseRecord.donatedAt = donationDate;
    await responseRecord.save();

    // 9. Update EmergencyRequest fulfillment metrics
    const requiredUnits = emergency.unitsRequired || emergency.unitsNeeded || 1;
    const currentFulfilled = emergency.unitsFulfilled || 0;
    const newFulfilledCount = currentFulfilled + 1;
    emergency.unitsFulfilled = newFulfilledCount;

    if (newFulfilledCount >= requiredUnits) {
      emergency.status = 'FULFILLED';
      emergency.isFulfilled = true;
      if (!emergency.fulfilledAt) {
        emergency.fulfilledAt = donationDate;
      }
    }
    await emergency.save();

    // 10. Update Donor: lastDonationDate, donationsCount, Karma & Badges
    donor.lastDonationDate = donationDate;
    const newCount = (donor.donationsCount || 0) + 1;
    donor.donationsCount = newCount;

    // Calculate Karma points (+100 base, +50 rare blood group bonus)
    const isRare = ['O-', 'AB-', 'B-', 'A-'].includes(donor.bloodGroup);
    const earnedKarma = 100 + (isRare ? 50 : 0);
    donor.karmaPoints = (donor.karmaPoints || 0) + earnedKarma;

    if (!donor.badges) donor.badges = [];
    const hasBadge = (bid) => donor.badges.some(b => b.badgeId === bid);

    // 1st donation badge
    if (newCount >= 1 && !hasBadge('BADGE_FIRST_DROP')) {
      donor.badges.push({
        badgeId: 'BADGE_FIRST_DROP',
        name: 'First Voluntary Donation',
        iconKey: 'ic_badge_first_drop',
        description: 'Completed your first verified life-saving blood donation.',
        awardedAt: donationDate
      });
    }

    // Rare guardian badge
    if (isRare && !hasBadge('BADGE_RARE_GUARDIAN')) {
      donor.badges.push({
        badgeId: 'BADGE_RARE_GUARDIAN',
        name: 'Rare Blood Guardian',
        iconKey: 'ic_badge_rare_guardian',
        description: 'Responded to critical need with a rare blood group.',
        awardedAt: donationDate
      });
    }

    // Silver Saver (3+ donations)
    if (newCount >= 3 && !hasBadge('BADGE_SILVER_SAVER')) {
      donor.badges.push({
        badgeId: 'BADGE_SILVER_SAVER',
        name: 'Silver Life Saver',
        iconKey: 'ic_badge_silver_saver',
        description: 'Achieved 3 verified life-saving blood donations.',
        awardedAt: donationDate
      });
    }

    // Gold Hero (5+ donations)
    if (newCount >= 5 && !hasBadge('BADGE_GOLD_HERO')) {
      donor.badges.push({
        badgeId: 'BADGE_GOLD_HERO',
        name: 'Gold Life Saver',
        iconKey: 'ic_badge_gold_hero',
        description: 'Distinguished lifesaver with 5+ verified blood donations.',
        awardedAt: donationDate
      });
    }

    await donor.save();

    // Safe Audit Logging
    logAuditEvent({
      actorId: verifier._id,
      actorRole: verifier.role || 'COORDINATOR',
      action: 'DONATION_VERIFIED',
      entityType: 'DonationHistory',
      entityId: donationHistory._id,
      metadata: {
        certificateId: certificateData.certificateId,
        donorId: donor._id,
        requestId: emergency._id,
        hospitalId: emergency.hospitalId,
        unitsFulfilled: newFulfilledCount,
        unitsRequired: requiredUnits
      }
    });

    // 11. Send Notifications
    notificationService.sendNotificationToUser(donor._id, {
      notificationType: 'DONATION_VERIFIED',
      requestId: emergency._id.toString(),
      certificateId: certificateData.certificateId,
      title: '🎉 Blood Donation Verified!',
      body: `Your blood donation at ${emergency.hospital} has been officially verified. Tap to view your certificate.`
    });

    if (emergency.requester) {
      notificationService.sendNotificationToUser(emergency.requester, {
        notificationType: 'DONATION_VERIFIED',
        requestId: emergency._id.toString(),
        title: '🩸 Blood Donation Verified',
        body: `A voluntary donor's blood donation for ${emergency.patientName} at ${emergency.hospital} was verified (${newFulfilledCount}/${requiredUnits} units completed).`
      });
    }

    return res.status(200).json({
      success: true,
      message: 'Donation successfully verified and certificate generated.',
      donation: donationHistory,
      certificateId: certificateData.certificateId,
      unitsFulfilled: newFulfilledCount,
      unitsRequired: requiredUnits,
      isFulfilled: emergency.isFulfilled
    });
  } catch (err) {
    console.error('Error verifying donation:', err);
    res.status(500).json({ success: false, message: err.message });
  }
});

// DELETE /api/emergency/:id - Protected: Creator or Admin only
router.delete('/:id', authenticateToken, async (req, res) => {
  try {
    const { id } = req.params;
    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ success: false, message: 'Invalid Emergency ID' });
    }

    const emergency = await EmergencyRequest.findById(id);
    if (!emergency) {
      return res.status(404).json({ success: false, message: 'Emergency request not found' });
    }

    // Ownership Verification: Caller must be the requester or matching email
    const callerId = req.user.id;
    const callerEmail = req.user.email;
    const isOwner =
      (emergency.requester && emergency.requester.toString() === callerId) ||
      (emergency.postedBy && emergency.postedBy.toLowerCase() === callerEmail.toLowerCase()) ||
      req.user.role === 'ADMIN';

    if (!isOwner) {
      return res.status(403).json({
        success: false,
        message: 'Forbidden: You do not have permission to cancel this emergency request.'
      });
    }

    // Mark as cancelled with audit timestamps
    const cancelledByRole = req.user.role === 'ADMIN' ? 'ADMIN' : 'REQUESTER';
    const resolutionReason = (req.body && req.body.reason) || 'Requirement fulfilled / Resolved by requester';

    emergency.status = 'CANCELLED';
    emergency.isActive = false;
    emergency.isFulfilled = true;
    if (!emergency.cancelledAt) {
      emergency.cancelledAt = new Date();
    }
    emergency.cancelledBy = req.user.id;
    emergency.cancelledByRole = cancelledByRole;
    emergency.cancelledReason = resolutionReason;
    await emergency.save();

    // Safely release any responding/en-route donors so their availability is immediately restored
    const activeResponses = await EmergencyResponse.find({
      $or: [{ requestId: emergency._id }, { emergencyRequestId: emergency._id }],
      status: { $in: ['ACCEPTED', 'TRAVELLING', 'NOTIFIED'] }
    });

    for (const resp of activeResponses) {
      resp.status = 'CANCELLED';
      resp.cancelledAt = new Date();
      resp.cancellationReason = `Emergency resolved: ${resolutionReason}`;
      await resp.save();

      // Restore donor availability immediately with zero penalty
      await User.findByIdAndUpdate(resp.donorId, { isAvailable: true });

      // Send courteous notification thanking the donor
      notificationService.sendNotificationToUser(resp.donorId, {
        notificationType: 'EMERGENCY_RESOLVED',
        requestId: emergency._id.toString(),
        title: '✅ Emergency SOS Resolved',
        body: `The blood requirement for ${emergency.patientName} at ${emergency.hospital} has been fulfilled. Thank you for your willingness to help!`
      });
    }

    logAuditEvent({
      actorId: req.user.id,
      actorRole: cancelledByRole,
      action: 'EMERGENCY_CANCELLED',
      entityType: 'EmergencyRequest',
      entityId: emergency._id,
      metadata: {
        cancelledReason: emergency.cancelledReason,
        patientName: emergency.patientName,
        releasedDonorsCount: activeResponses.length
      }
    });

    res.json({
      success: true,
      message: 'Emergency request resolved successfully. Responding donors have been notified and released.'
    });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

module.exports = router;
