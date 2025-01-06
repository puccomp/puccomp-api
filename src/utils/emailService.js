import nodemailer from 'nodemailer'

const transporter = nodemailer.createTransport({
  host: process.env.EMAIL_HOST,
  port: process.env.EMAIL_PORT,
  secure: false,
  auth: {
    user: process.env.EMAIL_USER,
    pass: process.env.EMAIL_PASS,
  },
})

export const sendEmail = async (to, subject, text, attachments = []) => {
  try {
    const info = await transporter.sendMail({
      from: `"COMP API" <${process.env.EMAIL_USER}>`,
      to,
      subject,
      text,
      attachments,
    })
  } catch (error) {
    console.error(`Error sending email: ${error.message}`)
    throw error
  }
}
