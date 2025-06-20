import nodemailer from 'nodemailer'
import { Attachment } from 'nodemailer/lib/mailer/index.js'

const transporter = nodemailer.createTransport({
  host: process.env.EMAIL_HOST,
  port: parseInt(process.env.EMAIL_PORT!, 10),
  secure: false,
  auth: {
    user: process.env.EMAIL_USER,
    pass: process.env.EMAIL_PASS,
  },
})

export const sendEmail = async (
  to: string | string[],
  subject: string,
  text: string,
  attachments: Attachment[] = []
): Promise<void> => {
  try {
    const info = await transporter.sendMail({
      from: `"COMP API" <${process.env.EMAIL_USER}>`,
      to,
      subject,
      text,
      attachments,
    })
    console.log(
      `Email sent: ${info.messageId} | ${info.response} | ${info.envelope.to.join(', ')} | --->  to: ${to}`
    )
  } catch (error) {
    console.error(`Error sending email: ${(error as Error).message}`)
    throw error
  }
}
