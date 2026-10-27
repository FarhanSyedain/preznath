package com.yimberzol.preznath.data

import com.yimberzol.preznath.domain.CorpusEntry
import org.json.JSONArray

object CorpusLoader {
    /** Built-in corpus so JVM unit tests work without Android AssetManager. */
    fun builtin(): List<CorpusEntry> = BUILTIN

    fun fromJson(json: String): List<CorpusEntry> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    CorpusEntry(
                        text = o.getString("text"),
                        category = o.getString("category"),
                        isScam = o.getBoolean("is_scam"),
                    ),
                )
            }
        }
    }

    private val BUILTIN: List<CorpusEntry> = listOf(
        // --- scams (~40) ---
        CorpusEntry("Dear customer, your SBI YONO account will be blocked today. Please update your KYC immediately by clicking the link.", "KYC expiry", true),
        CorpusEntry("Your HDFC bank account has been suspended due to incomplete KYC. Click here to re-activate within 24 hours.", "KYC expiry", true),
        CorpusEntry("Dear user your Paytm KYC has expired. Update now to avoid account blocking. Call customer care 98xxxxxxx.", "KYC expiry", true),
        CorpusEntry("Your PAN card is not linked with your bank account. Your account will be deactivated. Update PAN now.", "KYC expiry", true),
        CorpusEntry("Dear customer your net banking will be blocked today. Please update your PAN card details at the link.", "KYC expiry", true),
        CorpusEntry("Dear consumer your electricity power will be disconnected tonight at 9:30 pm because your previous month bill was not updated. Please contact our electricity officer.", "electricity disconnection", true),
        CorpusEntry("Your electricity connection will be cut off today at 8 PM. Last month's bill is pending. Call 70xxxxxxxx immediately to update.", "electricity disconnection", true),
        CorpusEntry("Bijli bill update nahi hua hai, aaj raat connection kaat diya jayega. Turant officer se sampark karein.", "electricity disconnection", true),
        CorpusEntry("I have sent you a payment request of Rs 5000. Please accept the request and enter your UPI PIN to receive the money.", "collect request trick", true),
        CorpusEntry("To receive the refund amount, approve the collect request in your PhonePe app and enter PIN.", "collect request trick", true),
        CorpusEntry("Sir I am paying you advance for the sofa. Please approve the request on Google Pay to get the money into your account.", "collect request trick", true),
        CorpusEntry("Mujhe aapko paise bhejne hain, Google Pay par request aayegi, usko approve karke PIN daal dena.", "collect request trick", true),
        CorpusEntry("Congratulations! You have received a cashback of Rs 2999 from PhonePe. Click the link to claim before it expires.", "fake cashback", true),
        CorpusEntry("Your Paytm cashback of Rs 1500 is pending. Scan this QR code to receive it in your wallet.", "fake cashback", true),
        CorpusEntry("Refund of Rs 4500 initiated for your failed transaction. Enter your UPI PIN on the link to receive.", "fake cashback", true),
        CorpusEntry("Amazon refund of Rs 3200 is waiting. Confirm your UPI ID and PIN to credit the amount.", "fake cashback", true),
        CorpusEntry("I am in Indian Army posted at border. I want to buy your bike. I will pay full advance via Google Pay. Share your UPI ID and accept my request.", "army OLX buyer", true),
        CorpusEntry("I am CRPF jawan, cannot come to see the item, will pay online. First I send Rs 1 to confirm then full amount. Approve request.", "army OLX buyer", true),
        CorpusEntry("Sir I am from army, posting transfer, selling my car urgently at low price. Pay token amount to this UPI to book.", "army OLX buyer", true),
        CorpusEntry("Congratulations! Your mobile number has won Rs 25,00,000 in KBC lucky draw. Call this number to claim your prize.", "lottery", true),
        CorpusEntry("You have been selected as the lucky winner of a Tata Safari. Pay registration fee of Rs 6500 to claim.", "lottery", true),
        CorpusEntry("Dear customer you won a reward of Rs 10000 from SBI rewards points. Redeem now before expiry by installing the app.", "lottery", true),
        CorpusEntry("Your parcel from FedEx is on hold at customs. Pay a customs clearance fee of Rs 1250 to release the shipment.", "parcel customs fee", true),
        CorpusEntry("India Post: your package could not be delivered due to incomplete address. Pay Rs 25 redelivery fee at the link.", "parcel customs fee", true),
        CorpusEntry("Your courier is held by Mumbai customs and contains illegal items. Call the officer immediately or face arrest.", "parcel customs fee", true),
        CorpusEntry("Part time job offer! Earn Rs 5000 daily by liking YouTube videos. Pay Rs 500 registration to start. WhatsApp now.", "job offer", true),
        CorpusEntry("Congratulations you are selected for work from home job at Amazon. Pay Rs 1999 for training kit to confirm your seat.", "job offer", true),
        CorpusEntry("Hiring! Data entry job, salary 30000 per month, no interview. Deposit security amount of Rs 2500 to this UPI.", "job offer", true),
        CorpusEntry("Mummy, your son had an accident and is in hospital. Doctor needs Rs 20000 immediately for operation. Send to this UPI.", "family emergency", true),
        CorpusEntry("Hello uncle, this is your nephew, I lost my phone, this is my new number. I urgently need Rs 15000, will return tomorrow.", "family emergency", true),
        CorpusEntry("Papa I am in police custody, please do not call, transfer Rs 50000 to this account to settle the matter now.", "family emergency", true),
        CorpusEntry("Dear SBI customer, your debit card has been blocked. Share the OTP sent to your mobile to unblock it.", "fake bank SMS", true),
        CorpusEntry("HDFC Bank: suspicious login detected. Verify your identity by sharing the 6 digit OTP with our executive.", "fake bank SMS", true),
        CorpusEntry("ICICI alert: Rs 49,999 debited from your account. If not done by you, call 1800xxxxxxx and share card details to reverse.", "fake bank SMS", true),
        CorpusEntry("Your Axis Bank credit card points worth Rs 8990 are expiring today. Redeem via the link by entering card number and CVV.", "fake bank SMS", true),
        CorpusEntry("For KYC verification install AnyDesk app from Play Store and share the 9 digit code with our executive.", "remote access app", true),
        CorpusEntry("Download the SBI YONO update from this link (apk) and complete verification to avoid account suspension.", "remote access app", true),
        CorpusEntry("Install Quick Support app and share the code so our executive can help you complete your Paytm KYC.", "remote access app", true),
        CorpusEntry("This is TRAI. Your mobile number is linked to illegal activity and will be disconnected in 2 hours. Press 9 to speak to officer.", "digital arrest", true),
        CorpusEntry("Mumbai Police cyber cell: a case has been registered against your Aadhaar. Transfer your funds to this RBI safe account for verification.", "digital arrest", true),
        CorpusEntry("Your Flipkart order is cancelled. For refund call customer care number 90xxxxxxxx and share OTP.", "fake customer care", true),
        CorpusEntry("Loan approved! Rs 2,00,000 instant loan at 0 percent interest. Pay processing fee of Rs 1500 to disburse.", "fake loan", true),
        // --- legit (~20) ---
        CorpusEntry("Rs 500.00 debited from A/c XX1234 on 12-Mar-24 to VPA ravi@okaxis UPI Ref 4078xxxxxxx. If not you, call 1800 1234.", "bank debit alert", false),
        CorpusEntry("Dear Customer, Rs 2,000.00 credited to your A/c XX5678 on 03-Apr-24 by UPI from priya@ybl. Avl Bal Rs 15,230.", "bank credit alert", false),
        CorpusEntry("Your OTP for HDFC NetBanking login is 482913. Valid for 10 minutes. Do not share this with anyone.", "bank OTP", false),
        CorpusEntry("123456 is your OTP to log in to PhonePe. Never share your OTP with anyone. PhonePe will never call you to ask for it.", "bank OTP", false),
        CorpusEntry("Hey, sending you 500 for dinner, check your GPay.", "friend payment", false),
        CorpusEntry("Bhai 250 bhej diya hai rent ke liye, confirm kar lena.", "friend payment", false),
        CorpusEntry("Can you send me 1200 for the movie tickets? I'll book them now. My UPI is arjun@okicici", "friend payment", false),
        CorpusEntry("Thanks for lunch! Paid you back 350 on PhonePe.", "friend payment", false),
        CorpusEntry("Your Amazon order #403-1234567 has been shipped and will arrive by Thursday. Track in the app.", "delivery notification", false),
        CorpusEntry("Swiggy: your order from Dominos is on the way. Rider Suresh will reach in 12 mins.", "delivery notification", false),
        CorpusEntry("Flipkart: Your package has been delivered. Rate your delivery experience in the app.", "delivery notification", false),
        CorpusEntry("Your electricity bill for March is Rs 1,240. Due date 20 Apr. Pay via the official MSEDCL app or website.", "utility bill", false),
        CorpusEntry("Reminder: your Jio recharge of Rs 299 expires on 15 May. Recharge from MyJio app.", "utility bill", false),
        CorpusEntry("Your monthly SIP of Rs 5000 in Axis Bluechip Fund was processed on 05-Apr. Units will reflect in 2 days.", "investment notification", false),
        CorpusEntry("SBI: your credit card statement for April is ready. Total due Rs 8,420, minimum due Rs 420. Pay by 25 Apr.", "bank statement", false),
        CorpusEntry("Meeting moved to 4 pm tomorrow. Bring the printed copies of the report.", "personal message", false),
        CorpusEntry("Happy birthday! Hope you have a great day. Let's catch up this weekend.", "personal message", false),
        CorpusEntry("Mom, I reached the hostel safely. Will call you after dinner.", "personal message", false),
        CorpusEntry("IRCTC: PNR 8412345678, Train 12951, Coach B3 Seat 42, CNF. Departure 16:35 from Mumbai Central.", "travel notification", false),
        CorpusEntry("Zomato: Rs 180 refund for order #91234 has been credited to your original payment method.", "refund notification", false),
        CorpusEntry("Your Uber trip receipt: Rs 240 paid via UPI. Rate your driver Mahesh.", "receipt", false),
    )
}
