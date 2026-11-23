package com.bytewise.ptp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Fixed wording for every reply that carries a commitment or ends the call. These are never left to the
 * model: it could mis-state an amount or date, or say "recorded" when nothing was saved.
 */
public final class Replies {

    /** What the agent still needs from the customer. */
    public enum Ask { BOTH, DATE, AMOUNT, PLAN }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private Replies() {
    }

    public static String ask(Ask ask, Language lang) {
        return switch (lang) {
            case ENGLISH -> switch (ask) {
                case BOTH -> "Can you please provide any tentative date and amount which you are going to pay?";
                case DATE -> "Can you please provide the date by which you will pay?";
                case AMOUNT -> "Can you please provide the amount you are going to pay?";
                case PLAN -> "Please tell me the amount, date and payment mode for each installment, and keep the "
                        + "total within your outstanding amount.";
            };
            case HINGLISH -> switch (ask) {
                case BOTH -> "Kya aap koi tentative tareekh aur amount bata sakte hain jo aap pay karenge?";
                case DATE -> "Aap kis tareekh tak pay karenge?";
                case AMOUNT -> "Aap kitne rupaye pay karenge?";
                case PLAN -> "Kripya har installment ka amount, tareekh aur payment mode batayein, aur total aapke "
                        + "outstanding amount se zyada na ho.";
            };
            case MARATHI -> switch (ask) {
                case BOTH -> "Tumhi kontya tarkhela ani kiti rupaye pay karnar, te sangu shakta ka?";
                case DATE -> "Tumhi kontya tarkhela pay karnar?";
                case AMOUNT -> "Tumhi kiti rupaye pay karnar?";
                case PLAN -> "Krupaya pratyek installment cha amount, tarkh ani payment mode sanga, ani total tumchya "
                        + "outstanding rakkamepeksha jast nasava.";
            };
        };
    }

    /** The same question was just asked and not answered: say it differently instead of repeating it word for word. */
    public static String askAgain(Ask ask, Language lang) {
        String what = switch (ask) {
            case DATE -> lang == Language.ENGLISH ? "a date" : lang == Language.HINGLISH ? "ek tareekh" : "ek tarkh";
            case AMOUNT -> lang == Language.ENGLISH ? "an amount" : lang == Language.HINGLISH ? "ek amount" : "ek rakkam";
            default -> lang == Language.ENGLISH ? "a date and an amount"
                    : lang == Language.HINGLISH ? "tareekh aur amount" : "tarkh ani rakkam";
        };
        return switch (lang) {
            case ENGLISH -> "I understand. Even a rough idea helps: " + what + " you can manage, say within this week or next?";
            case HINGLISH -> "Samajh sakta hoon. Andaaza hi chalega: " + what + " bata dijiye, jaise is hafte ya agle hafte?";
            case MARATHI -> "Samajte. Andaaj chalel: " + what + " sanga, jase ya athavdyat kinva pudhchya athavdyat.";
        };
    }

    /** Confirmation for a whole plan; a single installment gets the short form. */
    public static String confirm(PromisePlan plan, Language lang) {
        if (plan.installments().size() == 1) {
            Installment i = plan.first();
            return confirm(i.amount(), i.date(), lang);
        }
        String items = String.join("; ", plan.installments().stream().map(Replies::describe).toList());
        return switch (lang) {
            case ENGLISH -> "Thank you. I have noted your promises: " + items + ".";
            case HINGLISH -> "Dhanyavaad. Maine aapke vaade note kar liye hain: " + items + ".";
            case MARATHI -> "Dhanyavaad. Mi tumche vaade nondvle aahet: " + items + ".";
        };
    }

    private static String describe(Installment i) {
        String mode = i.mode() == null ? ""
                : " (" + ("UPI".equals(i.mode()) ? "UPI" : i.mode().toLowerCase(Locale.ROOT).replace('_', ' ')) + ")";
        return "Rs " + i.amount().stripTrailingZeros().toPlainString() + " on " + i.date().format(DATE) + mode;
    }

    private static String items(PromisePlan plan) {
        return String.join("; ", plan.installments().stream().map(Replies::describe).toList());
    }

    /** The opening line for an account that already has a promise on record. */
    public static String openingWithPromise(String name, BigDecimal outstanding, PromisePlan existing, Language lang) {
        String amt = outstanding.stripTrailingZeros().toPlainString();
        String what = items(existing);
        return switch (lang) {
            case ENGLISH -> "Hello " + name + ", this call is about your overdue loan. Your outstanding amount is Rs "
                    + amt + ". Our records show your earlier promise: " + what + ". Is that still correct?";
            case HINGLISH -> "Namaste " + name + ", yeh aapke overdue loan ke baare mein call hai. Aapka outstanding "
                    + "amount Rs " + amt + " hai. Hamare record mein aapka pichla vaada hai: " + what
                    + ". Kya yeh abhi bhi sahi hai?";
            case MARATHI -> "Namaskar " + name + ", hi tumchya overdue loan baddal call aahe. Tumchi outstanding rakkam "
                    + "Rs " + amt + " aahe. Amchya noindit tumcha aadhicha vaada aahe: " + what
                    + ". Ha ajunahi barobar aahe ka?";
        };
    }

    /** The answer to a customer who says they already gave a promise. */
    public static String existingPromise(PromisePlan existing, Language lang) {
        String what = items(existing);
        return switch (lang) {
            case ENGLISH -> "Yes, our records show your promise: " + what
                    + ". Is that still correct, or would you like to change it?";
            case HINGLISH -> "Ji haan, hamare record mein aapka vaada hai: " + what
                    + ". Kya yeh sahi hai, ya aap ise badalna chahte hain?";
            case MARATHI -> "Ho, amchya noindit tumcha vaada aahe: " + what
                    + ". Ha barobar aahe ka, ki tumhala badalaycha aahe?";
        };
    }

    /** When the customer wants to move a payment but it is not clear which one. */
    public static String askWhichInstallment(PromisePlan existing, Language lang) {
        String what = items(existing);
        return switch (lang) {
            case ENGLISH -> "Which payment would you like to move? Our records show: " + what + ".";
            case HINGLISH -> "Aap kaun si payment aage-peeche karna chahte hain? Hamare record mein hai: " + what + ".";
            case MARATHI -> "Kontha hapta badalaycha aahe? Amchya noindit aahe: " + what + ".";
        };
    }

    /** When the customer says the promise on record is only partly right. */
    public static String askWhatChanges(PromisePlan existing, Language lang) {
        String what = items(existing);
        return switch (lang) {
            case ENGLISH -> "Our records show: " + what + ". Which part needs to change? Tell me the payment and its new date or amount.";
            case HINGLISH -> "Hamare record mein hai: " + what + ". Kya badalna hai? Payment aur uski nayi tareekh ya amount batayein.";
            case MARATHI -> "Amchya noindit aahe: " + what + ". Kay badalaycha aahe? Hapta ani tyachi navin tarkh kinva rakkam sanga.";
        };
    }

    /** The closing line when the customer confirms the promise already on record. */
    public static String keptExisting(PromisePlan existing, Language lang) {
        String what = items(existing);
        return switch (lang) {
            case ENGLISH -> "Thank you. Your promise stays as recorded: " + what + ".";
            case HINGLISH -> "Dhanyavaad. Aapka vaada waisa hi rahega: " + what + ".";
            case MARATHI -> "Dhanyavaad. Tumcha vaada tasach rahil: " + what + ".";
        };
    }

    public static String confirm(BigDecimal amount, LocalDate date, Language lang) {
        String amt = amount.stripTrailingZeros().toPlainString();
        String d = date.format(DATE);
        return switch (lang) {
            case ENGLISH -> "Thank you. I have noted your promise of Rs " + amt + " on " + d + ".";
            case HINGLISH -> "Dhanyavaad. Maine aapka Rs " + amt + " ka vaada " + d + " ke liye note kar liya hai.";
            case MARATHI -> "Dhanyavaad. Mi tumcha Rs " + amt + " cha vaada " + d + " sathi nondvla aahe.";
        };
    }

    /** What the agent says first; the outstanding balance is quoted by code, never by the model. */
    public static String opening(String name, BigDecimal outstanding, Language lang) {
        String amt = outstanding.stripTrailingZeros().toPlainString();
        return switch (lang) {
            case ENGLISH -> "Hello " + name + ", this call is about your overdue loan. Your outstanding amount is Rs "
                    + amt + ". How much can you pay, and by which date?";
            case HINGLISH -> "Namaste " + name + ", yeh aapke overdue loan ke baare mein call hai. Aapka outstanding "
                    + "amount Rs " + amt + " hai. Aap kitna aur kis tareekh tak pay kar sakte hain?";
            case MARATHI -> "Namaskar " + name + ", hi tumchya overdue loan baddal call aahe. Tumchi outstanding rakkam "
                    + "Rs " + amt + " aahe. Tumhi kiti ani kontya tarkhela pay karu shakta?";
        };
    }

    /** The text of the SMS or WhatsApp message itself. The link comes from the payment gateway, never from the model. */
    public static String paymentLinkMessage(String name, BigDecimal outstanding, String url, Language lang) {
        String amt = outstanding.stripTrailingZeros().toPlainString();
        return switch (lang) {
            case ENGLISH -> "Hello " + name + ", pay your overdue loan securely here: " + url + " . Amount due: Rs "
                    + amt + ". You can pay the full amount or a part of it. The link is valid for 24 hours.";
            case HINGLISH -> "Namaste " + name + ", apna overdue loan yahan surakshit tarike se bharein: " + url
                    + " . Baaki rakam: Rs " + amt + ". Aap poori ya aanshik rakam bhar sakte hain. "
                    + "Link 24 ghante ke liye valid hai.";
            case MARATHI -> "Namaskar " + name + ", tumcha overdue loan ithe surakshit paddhatine bhara: " + url
                    + " . Baki rakkam: Rs " + amt + ". Tumhi purna kinva kahi rakkam bharu shakta. "
                    + "Link 24 tasansathi valid aahe.";
        };
    }

    /** What the agent says after the link has gone out. */
    public static String linkSent(Channel channel, String last4, BigDecimal outstanding, Language lang) {
        String amt = outstanding.stripTrailingZeros().toPlainString();
        String how = channel.display();
        return switch (lang) {
            case ENGLISH -> "Thank you. I have noted your promise to pay today and sent a payment link on " + how
                    + " to the number ending " + last4 + ". You can pay the full Rs " + amt + " or a part of it.";
            case HINGLISH -> "Dhanyavaad. Maine aaj ka aapka vaada note kar liya hai aur " + how
                    + " par payment link bhej diya hai, number " + last4 + " par. Aap poore Rs " + amt
                    + " ya uska kuch hissa bhar sakte hain.";
            case MARATHI -> "Dhanyavaad. Mi tumcha aajcha vaada nondvla aahe ani " + how + " var payment link pathavli "
                    + "aahe, " + last4 + " ya kramankavar. Tumhi purna Rs " + amt + " kinva tyacha kahi bhag bharu shakta.";
        };
    }

    /** Added after the link message when the customer also promised later installments in the same breath. */
    public static String alsoNoted(PromisePlan plan, Language lang) {
        String later = String.join("; ", plan.installments().stream().skip(1).map(Replies::describe).toList());
        return switch (lang) {
            case ENGLISH -> " I have also noted: " + later + ".";
            case HINGLISH -> " Maine yeh bhi note kiya hai: " + later + ".";
            case MARATHI -> " Mi he pan nondvle aahe: " + later + ".";
        };
    }

    public static String linkFailed(Language lang) {
        return switch (lang) {
            case ENGLISH -> "Your promise to pay today is noted, but I could not send the payment link. "
                    + "A colleague will share it with you shortly.";
            case HINGLISH -> "Aaj ka aapka vaada note ho gaya hai, lekin main payment link nahi bhej paya. "
                    + "Hamare ek colleague aapko jaldi link bhejenge.";
            case MARATHI -> "Tumcha aajcha vaada nondvla aahe, pan mi payment link pathavu shakle nahi. "
                    + "Aamcha ek sahkari lavkarach link pathavel.";
        };
    }

    public static String noMobile(Language lang) {
        return switch (lang) {
            case ENGLISH -> "I do not have a registered mobile number to send the link to. "
                    + "A colleague will contact you shortly.";
            case HINGLISH -> "Mere paas link bhejne ke liye registered mobile number nahi hai. "
                    + "Hamare ek colleague aapse jaldi sampark karenge.";
            case MARATHI -> "Link pathavnyasathi mazyakade nondnikrut mobile kramank nahi. "
                    + "Aamcha ek sahkari tumchyashi lavkarach sampark karel.";
        };
    }

    public static String nothingDue(Language lang) {
        return switch (lang) {
            case ENGLISH -> "There is no amount due on your account right now. Thank you.";
            case HINGLISH -> "Abhi aapke account par koi rakam baaki nahi hai. Dhanyavaad.";
            case MARATHI -> "Sadhya tumchya khatyavar koni rakkam baki nahi. Dhanyavaad.";
        };
    }

    public static String amountTooHigh(BigDecimal outstanding, Language lang) {
        String amt = outstanding.stripTrailingZeros().toPlainString();
        return switch (lang) {
            case ENGLISH -> "Your outstanding amount is Rs " + amt + ". Please tell me an amount up to that.";
            case HINGLISH -> "Aapka outstanding amount Rs " + amt + " hai. Kripya iske andar ka amount batayein.";
            case MARATHI -> "Tumchi outstanding rakkam Rs " + amt + " aahe. Krupaya tyapeksha kami rakkam sanga.";
        };
    }

    public static String escalated(Language lang) {
        return switch (lang) {
            case ENGLISH -> "A colleague will contact you shortly. Thank you.";
            case HINGLISH -> "Hamare ek colleague aapse jaldi sampark karenge. Dhanyavaad.";
            case MARATHI -> "Aamcha ek sahkari tumchyashi lavkarach sampark karel. Dhanyavaad.";
        };
    }

    public static String ended(Language lang) {
        return switch (lang) {
            case ENGLISH -> "This call has already ended.";
            case HINGLISH -> "Yeh call khatam ho chuka hai.";
            case MARATHI -> "Ha call sampla aahe.";
        };
    }
}
