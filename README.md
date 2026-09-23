# Quick Guard Pro

Installment device-management system for **Abbas Khan Electronics**.

Two Android apps + a Firebase backend:

- **QG Pro Admin** (`…quickguardpro.admin`) — shop app: customers, agreements, payments, device lock/unlock/location, staff roles.
- **Quick Guard Pro** (`…quickguardpro.device`) — installed on the customer's phone as **Device Owner** via QR enrollment.
- **Cloud Functions backend** — all financial and security logic; the apps never write to the database directly.

> Full step-by-step setup is in **SETUP.md** (Urdu + English).
> Honest limits and things still to test are in **LIMITATIONS.md**. Please read both.

## What it does
- Installment agreements, payments, receipts, reversals, auto status (due/overdue/paid).
- QR Device-Owner enrollment with a single-use expiring code and a clear consent screen.
- Lock (payment-restriction screen with emergency calls), unlock, one-time location on request, messages.
- SIM-change warning in Urdu; daily Urdu reminders; audit log of every action.
- Automatic release after the final payment.

## Design
Dark charcoal + red + silver theme, shield-and-padlock icon. No hidden tracking: only the disclosed
status fields, and location only once when the shop requests it.
