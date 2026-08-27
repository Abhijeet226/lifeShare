/**
 * Donor Donation Eligibility & 90-Day Post-Donation Cooldown Policy Service
 */

const DONATION_COOLDOWN_DAYS = parseInt(process.env.DONATION_COOLDOWN_DAYS || '90', 10);

/**
 * Check whether a donor is eligible to donate blood based on their last donation date.
 * @param {Object} user - User document or object with lastDonationDate
 * @param {Date} [now=new Date()] - Evaluation timestamp
 * @returns {Object} eligibility assessment result
 */
function checkDonorEligibility(user, now = new Date()) {
  if (!user || !user.lastDonationDate) {
    return {
      isEligible: true,
      daysRemaining: 0,
      nextEligibleDate: null,
      lastDonationDate: null,
      cooldownDays: DONATION_COOLDOWN_DAYS
    };
  }

  const lastDate = new Date(user.lastDonationDate);
  const nextEligibleTime = lastDate.getTime() + (DONATION_COOLDOWN_DAYS * 24 * 60 * 60 * 1000);
  const currentTime = now.getTime();

  if (currentTime >= nextEligibleTime) {
    return {
      isEligible: true,
      daysRemaining: 0,
      nextEligibleDate: null,
      lastDonationDate: lastDate,
      cooldownDays: DONATION_COOLDOWN_DAYS
    };
  }

  const msRemaining = nextEligibleTime - currentTime;
  const daysRemaining = Math.ceil(msRemaining / (1000 * 60 * 60 * 24));
  const nextEligibleDate = new Date(nextEligibleTime);

  return {
    isEligible: false,
    daysRemaining,
    nextEligibleDate,
    lastDonationDate: lastDate,
    cooldownDays: DONATION_COOLDOWN_DAYS
  };
}

/**
 * Returns the cutoff Date before which lastDonationDate must be for a donor to be eligible.
 * @param {Date} [now=new Date()]
 * @returns {Date} cutoff date
 */
function getCooldownCutoffDate(now = new Date()) {
  return new Date(now.getTime() - (DONATION_COOLDOWN_DAYS * 24 * 60 * 60 * 1000));
}

const User = require('../models/User');
const notificationService = require('./notificationService');

/**
 * Scan donors whose 90-day cooldown period has just expired and send re-engagement alert.
 */
async function checkAndNotifyExpiredCooldowns() {
  try {
    const cutoffDate = getCooldownCutoffDate();
    // Donors whose lastDonationDate is older than cutoff and haven't been notified yet or available is false
    const eligibleDonors = await User.find({
      lastDonationDate: { $ne: null, $lte: cutoffDate },
      role: 'DONOR',
      cooldownNotified: { $ne: true }
    }).limit(50);

    let notifiedCount = 0;
    for (const donor of eligibleDonors) {
      await notificationService.sendToUser(donor._id, {
        title: '🌟 Ready to Save Lives Again!',
        body: 'Your 90-day donation cooldown has completed and your red blood cells are fully replenished. You are now eligible to donate blood again!',
        data: {
          type: 'COOLDOWN_EXPIRED',
          userId: donor._id.toString()
        },
        notificationType: 'COOLDOWN_EXPIRED'
      });

      donor.cooldownNotified = true;
      await donor.save();
      notifiedCount++;
    }

    return { success: true, notifiedCount };
  } catch (err) {
    console.error('Error checking expired cooldowns:', err);
    return { success: false, error: err.message };
  }
}

module.exports = {
  DONATION_COOLDOWN_DAYS,
  checkDonorEligibility,
  getCooldownCutoffDate,
  checkAndNotifyExpiredCooldowns
};
