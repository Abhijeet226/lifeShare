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

module.exports = {
  DONATION_COOLDOWN_DAYS,
  checkDonorEligibility,
  getCooldownCutoffDate
};
