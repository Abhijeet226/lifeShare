/**
 * LifeShare Blood Bank - Live SMS OTP Delivery Service
 * Supports Fast2SMS (India), Twilio (Global), 2Factor.in (India), and Custom Gateways
 */

/**
 * Clean phone numbers to pure digits or E.164 international format
 */
function cleanPhoneNumber(rawPhone) {
  if (!rawPhone) return '';
  let cleaned = rawPhone.toString().trim();
  // Remove spaces, hyphens, parentheses
  cleaned = cleaned.replace(/[\s\-\(\)]/g, '');
  return cleaned;
}

/**
 * Send SMS via Fast2SMS (Popular high-delivery Indian SMS Gateway)
 */
async function sendViaFast2Sms(phone, otp, purpose) {
  const apiKey = process.env.FAST2SMS_API_KEY;
  if (!apiKey) return null;

  let digits = cleanPhoneNumber(phone);
  if (digits.startsWith('+91')) {
    digits = digits.substring(3);
  } else if (digits.startsWith('91') && digits.length === 12) {
    digits = digits.substring(2);
  }

  const payload = {
    route: 'otp',
    variables_values: otp,
    numbers: digits
  };

  const response = await fetch('https://www.fast2sms.com/dev/bulkV2', {
    method: 'POST',
    headers: {
      'authorization': apiKey,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  const data = await response.json();
  if (data.return === true || response.ok) {
    return { success: true, provider: 'Fast2SMS', data };
  } else {
    throw new Error(data.message || JSON.stringify(data));
  }
}

/**
 * Send SMS via Twilio (Global SMS Gateway)
 */
async function sendViaTwilio(phone, otp, purpose) {
  const accountSid = process.env.TWILIO_ACCOUNT_SID;
  const authToken = process.env.TWILIO_AUTH_TOKEN;
  const fromNumber = process.env.TWILIO_PHONE_NUMBER;

  if (!accountSid || !authToken || !fromNumber) return null;

  let toPhone = cleanPhoneNumber(phone);
  if (!toPhone.startsWith('+')) {
    toPhone = '+91' + toPhone; // Default to India country code if unformatted
  }

  const messageBody = `[LifeShare] Your verification OTP code is ${otp}. Valid for 10 minutes. Do not share this code with anyone.`;
  const url = `https://api.twilio.com/2010-04-01/Accounts/${accountSid}/Messages.json`;

  const params = new URLSearchParams();
  params.append('To', toPhone);
  params.append('From', fromNumber);
  params.append('Body', messageBody);

  const authHeader = 'Basic ' + Buffer.from(`${accountSid}:${authToken}`).toString('base64');

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Authorization': authHeader,
      'Content-Type': 'application/x-www-form-urlencoded'
    },
    body: params.toString()
  });

  const data = await response.json();
  if (response.ok && data.sid) {
    return { success: true, provider: 'Twilio', sid: data.sid };
  } else {
    throw new Error(data.message || 'Twilio dispatch failed');
  }
}

/**
 * Send SMS via 2Factor.in (India SMS Gateway)
 */
async function sendVia2Factor(phone, otp) {
  const apiKey = process.env.TWO_FACTOR_API_KEY;
  if (!apiKey) return null;

  let digits = cleanPhoneNumber(phone);
  if (digits.startsWith('+91')) {
    digits = digits.substring(3);
  }

  const url = `https://2factor.in/v3/${apiKey}/SMS/${digits}/${otp}/OTP1`;
  const response = await fetch(url);
  const data = await response.json();

  if (data.Status === 'Success') {
    return { success: true, provider: '2Factor.in', data };
  } else {
    throw new Error(data.Details || '2Factor dispatch failed');
  }
}

/**
 * Dispatch real OTP SMS to recipient mobile number
 * @param {string} phone - Recipient phone number (e.g. +91 9876543210 or 9876543210)
 * @param {string} otp - 6-digit OTP code
 * @param {string} purpose - Purpose of OTP
 * @returns {Promise<{success: boolean, message: string, simulated?: boolean, provider?: string}>}
 */
async function sendOtpSms(phone, otp, purpose = 'Verification') {
  const cleaned = cleanPhoneNumber(phone);
  if (!cleaned || cleaned.length < 10) {
    return { success: false, message: 'Invalid phone number format' };
  }

  // 1. Try Fast2SMS if configured
  if (process.env.FAST2SMS_API_KEY) {
    try {
      const res = await sendViaFast2Sms(phone, otp, purpose);
      console.log(`✅ [LIVE SMS SENT via Fast2SMS] Delivered OTP to ${cleaned}`);
      return { success: true, simulated: false, provider: 'Fast2SMS', message: `SMS delivered to ${phone}` };
    } catch (err) {
      console.error(`❌ [Fast2SMS ERROR]`, err.message);
    }
  }

  // 2. Try Twilio if configured
  if (process.env.TWILIO_ACCOUNT_SID && process.env.TWILIO_AUTH_TOKEN) {
    try {
      const res = await sendViaTwilio(phone, otp, purpose);
      console.log(`✅ [LIVE SMS SENT via Twilio] Delivered OTP to ${cleaned}`);
      return { success: true, simulated: false, provider: 'Twilio', message: `SMS delivered to ${phone}` };
    } catch (err) {
      console.error(`❌ [Twilio ERROR]`, err.message);
    }
  }

  // 3. Try 2Factor.in if configured
  if (process.env.TWO_FACTOR_API_KEY) {
    try {
      const res = await sendVia2Factor(phone, otp);
      console.log(`✅ [LIVE SMS SENT via 2Factor] Delivered OTP to ${cleaned}`);
      return { success: true, simulated: false, provider: '2Factor', message: `SMS delivered to ${phone}` };
    } catch (err) {
      console.error(`❌ [2Factor ERROR]`, err.message);
    }
  }

  // Fallback: Console Logging with instructions for live SMS API key setup
  console.warn(`\n=============================================================`);
  console.warn(`📱 [REAL SMS OTP SIMULATION]`);
  console.warn(`Mobile Number: ${cleaned}`);
  console.warn(`Purpose:       ${purpose}`);
  console.warn(`OTP Code:      👉  ${otp}  👈`);
  console.warn(`To send LIVE SMS to mobile phones, add FAST2SMS_API_KEY or TWILIO credentials in backend/.env`);
  console.warn(`=============================================================\n`);

  return {
    success: true,
    simulated: true,
    message: `OTP generated for ${cleaned}. Set FAST2SMS_API_KEY or TWILIO_ACCOUNT_SID in .env for live SMS delivery.`
  };
}

module.exports = {
  sendOtpSms,
  cleanPhoneNumber
};
