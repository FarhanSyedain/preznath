import type { Example } from "./index";

export const EXAMPLES: Example[] = [
  {
    id: "kyc",
    title: "SBI KYC block",
    expected: "FRAUD",
    text: "Dear customer your SBI YONO account will be blocked today. Update KYC immediately: http://bit.ly/sbi-kyc-update or call 9876543210",
  },
  {
    id: "collect",
    title: "Collect-request trick",
    expected: "FRAUD",
    text: "Sir I am in Indian Army, want to buy your sofa from OLX. I am sending Rs 12,000 advance. Please approve the request on Google Pay and enter your UPI PIN to receive the money.",
    upi_id: "armyofficer.pay@okaxls",
  },
  {
    id: "cashback",
    title: "Pending cashback",
    expected: "SUSPICIOUS",
    text: "Your PhonePe cashback of Rs 1,499 is pending. Claim it within 24 hours from your rewards section.",
  },
  {
    id: "dinner",
    title: "Friend paying for dinner",
    expected: "SAFE",
    text: "Hey, sending you 500 for dinner, check your GPay",
    upi_id: "priya.s@okicici",
  },
  {
    id: "hindi",
    title: "Hindi electricity scam",
    expected: "FRAUD",
    text: "प्रिय उपभोक्ता, आपका बिजली बिल अपडेट नहीं हुआ है। आज रात 9:30 बजे आपका बिजली कनेक्शन काट दिया जाएगा। तुरंत हमारे अधिकारी से 9123456789 पर संपर्क करें और भुगतान करें।",
  },
];
