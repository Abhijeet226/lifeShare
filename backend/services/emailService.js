const nodemailer = require('nodemailer');

/**
 * LifeShare Blood Bank - Live Email Delivery Service
 */

function getTransporter() {
  const user = process.env.SMTP_USER;
  const pass = process.env.SMTP_PASS;

  if (!user || !pass) {
    return null;
  }

  // Custom SMTP Host vs Well-known Service (e.g., Gmail, Outlook)
  if (process.env.SMTP_HOST) {
    return nodemailer.createTransport({
      host: process.env.SMTP_HOST,
      port: parseInt(process.env.SMTP_PORT || '587', 10),
      secure: process.env.SMTP_SECURE === 'true',
      auth: { user, pass },
      tls: { rejectUnauthorized: false }
    });
  }

  return nodemailer.createTransport({
    service: process.env.SMTP_SERVICE || 'gmail',
    auth: { user, pass }
  });
}

/**
 * Generate a responsive, branded HTML email template for LifeShare OTPs
 */
function createOtpEmailHtml(otp, purpose = 'Verification', recipient = '') {
  return `
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>LifeShare Security Code</title>
  <style>
    body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #F8F9FA; color: #1E293B; }
    .container { max-width: 560px; margin: 30px auto; background: #FFFFFF; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.06); border: 1px solid #E2E8F0; }
    .header { background: linear-gradient(135deg, #D32F2F 0%, #B71C1C 100%); padding: 32px 24px; text-align: center; color: #FFFFFF; }
    .header h1 { margin: 0; font-size: 24px; font-weight: 800; letter-spacing: -0.5px; }
    .header p { margin: 6px 0 0; font-size: 14px; opacity: 0.9; }
    .content { padding: 32px 28px; }
    .intro { font-size: 15px; line-height: 1.6; color: #334155; margin-bottom: 24px; }
    .otp-box { background: #FFF5F5; border: 2px dashed #EF4444; border-radius: 12px; padding: 20px; text-align: center; margin: 24px 0; }
    .otp-label { font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 1.5px; color: #B91C1C; margin-bottom: 8px; }
    .otp-code { font-size: 38px; font-weight: 900; letter-spacing: 10px; color: #D32F2F; font-family: 'Courier New', Courier, monospace; margin: 4px 0; }
    .otp-expiry { font-size: 12px; color: #64748B; margin-top: 8px; }
    .warning { background: #F1F5F9; border-left: 4px solid #64748B; padding: 12px 16px; border-radius: 6px; font-size: 13px; color: #475569; line-height: 1.5; margin-top: 24px; }
    .footer { background: #F8FAFC; border-top: 1px solid #E2E8F0; padding: 20px; text-align: center; font-size: 12px; color: #94A3B8; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1>🩸 LifeShare Blood Bank</h1>
      <p>Saving Lives Together • Smart Voluntary Donor Network</p>
    </div>
    <div class="content">
      <p class="intro">Hello <strong>${recipient || 'LifeShare User'}</strong>,</p>
      <p class="intro">You requested a one-time verification code for <strong>${purpose}</strong>. Please enter the 6-digit code below in the LifeShare mobile app:</p>
      
      <div class="otp-box">
        <div class="otp-label">Your One-Time Password (OTP)</div>
        <div class="otp-code">${otp}</div>
        <div class="otp-expiry">⏳ Valid for the next 10 minutes</div>
      </div>

      <div class="warning">
        🔒 <strong>Security Warning:</strong> Never share this code with anyone. LifeShare staff will never ask for your OTP or password.
      </div>
    </div>
    <div class="footer">
      © ${new Date().getFullYear()} LifeShare Blood Bank Organization. All rights reserved.<br>
      This is an automated security transmission. Please do not reply to this email.
    </div>
  </div>
</body>
</html>
  `;
}

/**
 * Dispatch real OTP email to recipient
 * @param {string} toEmail - Recipient email address
 * @param {string} otp - 6-digit OTP string
 * @param {string} purpose - Purpose description (e.g., 'Account Registration', 'Password Reset', 'Profile Verification')
 * @returns {Promise<{success: boolean, message: string, simulated?: boolean}>}
 */
async function sendOtpEmail(toEmail, otp, purpose = 'Verification') {
  if (!toEmail || !toEmail.includes('@')) {
    return { success: false, message: 'Invalid recipient email address' };
  }

  const cleanEmail = toEmail.trim().toLowerCase();
  const transporter = getTransporter();

  if (!transporter) {
    console.warn(`\n=============================================================`);
    console.warn(`📧 [REAL EMAIL OTP SIMULATION]`);
    console.warn(`Recipient: ${cleanEmail}`);
    console.warn(`Purpose:   ${purpose}`);
    console.warn(`OTP Code:  👉  ${otp}  👈`);
    console.warn(`To send LIVE emails to inboxes, add SMTP_USER & SMTP_PASS in backend/.env`);
    console.warn(`=============================================================\n`);

    return {
      success: true,
      simulated: true,
      message: `OTP generated. Configure SMTP_USER and SMTP_PASS in .env for live inbox delivery.`
    };
  }

  try {
    const fromAddress = process.env.SMTP_FROM || `"LifeShare Blood Bank" <${process.env.SMTP_USER}>`;
    const mailOptions = {
      from: fromAddress,
      to: cleanEmail,
      subject: `LifeShare ${purpose} code`,
      text: `Your LifeShare ${purpose} verification code is: ${otp}. It will expire in 10 minutes. Never share this code.`,
      html: createOtpEmailHtml(otp, purpose, cleanEmail)
    };

    const info = await transporter.sendMail(mailOptions);
    console.log(`✅ [LIVE EMAIL SENT] Successfully delivered OTP to ${cleanEmail} (MsgID: ${info.messageId})`);

    return {
      success: true,
      simulated: false,
      messageId: info.messageId,
      message: `Real email successfully delivered to ${cleanEmail}`
    };
  } catch (error) {
    console.error(`❌ [EMAIL DISPATCH ERROR] Failed to send email to ${cleanEmail}:`, error.message);
    return {
      success: false,
      error: error.message,
      message: `Failed to deliver email: ${error.message}`
    };
  }
}

module.exports = {
  sendOtpEmail,
  createOtpEmailHtml
};
