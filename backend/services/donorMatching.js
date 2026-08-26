const User = require('../models/User');
const EmergencyRequest = require('../models/EmergencyRequest');
const EmergencyResponse = require('../models/EmergencyResponse');
const notificationService = require('./notificationService');
const { calculateDistanceMeters } = require('./locationService');
const { getCooldownCutoffDate } = require('./cooldownService');

const RADIUS_STAGES_METERS = [5000, 10000, 20000, 30000];
const MAX_DONORS_PER_BATCH = 10;

/**
 * Transfusion compatibility matrix (Donor -> Recipient)
 */
function getCompatibleDonorBloodGroups(recipientGroup) {
  switch (recipientGroup) {
    case 'O-':
      return ['O-'];
    case 'O+':
      return ['O+', 'O-'];
    case 'A-':
      return ['A-', 'O-'];
    case 'A+':
      return ['A+', 'A-', 'O+', 'O-'];
    case 'B-':
      return ['B-', 'O-'];
    case 'B+':
      return ['B+', 'B-', 'O+', 'O-'];
    case 'AB-':
      return ['AB-', 'A-', 'B-', 'O-'];
    case 'AB+':
      return ['AB+', 'AB-', 'A+', 'A-', 'B+', 'B-', 'O+', 'O-'];
    default:
      return [recipientGroup];
  }
}

/**
 * Progressive matching engine for an Emergency SOS request
 */
async function findMatchingDonors(emergencyId, stageIndex = 0) {
  try {
    const emergency = await EmergencyRequest.findById(emergencyId);
    if (!emergency) {
      console.warn(`[MATCHING] Emergency ${emergencyId} not found`);
      return { count: 0, matched: [] };
    }

    // Check current accepted count for multi-unit emergencies
    const acceptedCount = await EmergencyResponse.countDocuments({
      requestId: emergency._id,
      status: 'ACCEPTED'
    });
    const requiredUnits = emergency.unitsRequired || emergency.unitsNeeded || 1;

    // Stop matching only if fully fulfilled, cancelled, or expired
    if (emergency.isFulfilled || acceptedCount >= requiredUnits || ['CANCELLED', 'EXPIRED', 'COMPLETED', 'DONATION_COMPLETED'].includes(emergency.status)) {
      console.log(`[MATCHING] Emergency ${emergencyId} is resolved (${acceptedCount}/${requiredUnits} accepted, status: ${emergency.status}). Halting matching.`);
      return { count: 0, matched: [], status: emergency.status, acceptedCount, requiredUnits };
    }

    // Determine target coordinates [lng, lat]: Prefer authoritative hospitalLocation (where blood is needed), fallback to requestLocation
    let coordinates = null;
    if (emergency.hospitalLocation && emergency.hospitalLocation.coordinates && emergency.hospitalLocation.coordinates.length === 2) {
      coordinates = emergency.hospitalLocation.coordinates;
    } else if (emergency.requestLocation && emergency.requestLocation.coordinates && emergency.requestLocation.coordinates.length === 2) {
      coordinates = emergency.requestLocation.coordinates;
    }

    const compatibleGroups = getCompatibleDonorBloodGroups(emergency.bloodGroup);
    const radiusMeters = RADIUS_STAGES_METERS[stageIndex] || emergency.searchRadiusMeters || 10000;

    // Get list of donors already notified
    const existingResponses = await EmergencyResponse.find({ requestId: emergency._id }).select('donorId');
    const excludedDonorIds = existingResponses.map((r) => r.donorId);

    // 1. Exclude requester by ID
    if (emergency.requester) {
      excludedDonorIds.push(emergency.requester);
    }

    // 2. Exclude requester by Email (postedBy)
    if (emergency.postedBy && emergency.postedBy.includes('@')) {
      const creatorByEmail = await User.findOne({ email: emergency.postedBy.toLowerCase().trim() }).select('_id');
      if (creatorByEmail && !excludedDonorIds.some((id) => id.toString() === creatorByEmail._id.toString())) {
        excludedDonorIds.push(creatorByEmail._id);
      }
    }

    // 3. Exclude requester by Contact Phone Number
    if (emergency.contactNumber) {
      const cleanDigits = emergency.contactNumber.replace(/[^0-9]/g, '');
      if (cleanDigits.length >= 10) {
        const creatorByPhone = await User.findOne({ mobile: { $regex: cleanDigits.slice(-10) } }).select('_id');
        if (creatorByPhone && !excludedDonorIds.some((id) => id.toString() === creatorByPhone._id.toString())) {
          excludedDonorIds.push(creatorByPhone._id);
        }
      }
    }

    let candidates = [];
    const cooldownCutoff = getCooldownCutoffDate();

    if (coordinates) {
      const [lng, lat] = coordinates;
      // 1. Geospatial search via MongoDB $geoNear with Trust, Account & 90-Day Cooldown enforcement
      candidates = await User.aggregate([
        {
          $geoNear: {
            near: {
              type: 'Point',
              coordinates: [lng, lat]
            },
            distanceField: 'distanceMeters',
            maxDistance: radiusMeters,
            spherical: true,
            query: {
              _id: { $nin: excludedDonorIds },
              isAvailable: true,
              accountStatus: 'ACTIVE',
              bloodGroup: { $in: compatibleGroups },
              $or: [
                { lastDonationDate: null },
                { lastDonationDate: { $lte: cooldownCutoff } }
              ]
            }
          }
        },
        {
          $addFields: {
            // 1. Prioritize direct blood group match over universal donors
            isDirectMatch: { $cond: [{ $eq: ['$bloodGroup', emergency.bloodGroup] }, 1, 0] },
            // 2. Prioritize verified donors
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
            isDirectMatch: -1,
            verificationRank: -1,
            distanceMeters: 1,
            locationUpdatedAt: -1,
            lastActiveAt: -1
          }
        },
        {
          $limit: MAX_DONORS_PER_BATCH
        }
      ]);
    } else {
      // Fallback to cityId matching if no GPS coordinates on request
      const cityQuery = emergency.cityId ? { cityId: emergency.cityId } : {};
      candidates = await User.find({
        _id: { $nin: excludedDonorIds },
        isAvailable: true,
        accountStatus: 'ACTIVE',
        bloodGroup: { $in: compatibleGroups },
        ...cityQuery,
        $or: [
          { lastDonationDate: null },
          { lastDonationDate: { $lte: cooldownCutoff } }
        ]
      })
        .sort({ updatedAt: -1 })
        .limit(MAX_DONORS_PER_BATCH);
    }

    if (!candidates || candidates.length === 0) {
      console.log(`[MATCHING] Stage ${stageIndex + 1} (${radiusMeters / 1000} km): 0 donors found for ${emergency.bloodGroup}.`);
      if (stageIndex < RADIUS_STAGES_METERS.length - 1) {
        // Expand to next stage radius
        return findMatchingDonors(emergencyId, stageIndex + 1);
      } else {
        await EmergencyRequest.findByIdAndUpdate(emergencyId, {
          status: existingResponses.length > 0 ? 'DONORS_NOTIFIED' : 'NO_DONOR_FOUND'
        });
        return { count: 0, matched: [], radiusMeters };
      }
    }

    console.log(`[MATCHING] Found ${candidates.length} candidate donors for Emergency ${emergencyId} at ${radiusMeters / 1000} km radius.`);

    // Record response objects atomically & dispatch FCM notifications
    const notifiedDonors = [];
    for (const donor of candidates) {
      try {
        await EmergencyResponse.updateOne(
          { requestId: emergency._id, donorId: donor._id },
          { $setOnInsert: { status: 'NOTIFIED', notifiedAt: new Date() } },
          { upsert: true }
        );

        notifiedDonors.push({
          id: donor._id,
          name: donor.name,
          bloodGroup: donor.bloodGroup,
          distanceMeters: donor.distanceMeters || 0,
          verificationStatus: donor.verificationStatus || 'UNVERIFIED'
        });

        // Send FCM notification
        notificationService.sendEmergencyNotification(donor._id, emergency);
      } catch (dupErr) {
        // Ignore duplicate index errors
      }
    }

    // Update emergency status if still in searching state
    if (emergency.status === 'SEARCHING' || emergency.status === 'REQUESTED') {
      await EmergencyRequest.findByIdAndUpdate(emergencyId, {
        status: 'DONORS_NOTIFIED',
        searchRadiusMeters: radiusMeters
      });
    }

    return {
      count: notifiedDonors.length,
      matched: notifiedDonors,
      radiusMeters
    };
  } catch (err) {
    console.error('❌ Error in findMatchingDonors:', err.message);
    return { count: 0, error: err.message };
  }
}

module.exports = {
  findMatchingDonors,
  getCompatibleDonorBloodGroups,
  RADIUS_STAGES_METERS
};
