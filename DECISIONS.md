# Decisions

## Member Withdrawal

- Member withdrawal uses soft delete semantics with `members.withdrawn_at`.
- Withdrawn members are anonymized in-place so the original email can be reused for a later signup.
- OAuth members also anonymize `provider_id` on withdrawal to release the generated provider unique key.
- `orders.member_id` keeps its foreign key to preserve referential integrity.

## Privacy Boundary

- Withdrawal currently anonymizes identifiers inside `members`.
- If long-term retention of profile-like personal data becomes necessary, split that data into a separate `member_profiles` structure with its own retention policy.
