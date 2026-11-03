# Diagram feature examples

Open `database/public/diagrams/overview.erd.yaml` and refresh its preview.
The existing layout, groups, and saved viewport are preserved. The new
`public.diagram_examples` card starts below the identity tables; use Fit view
to find it, then double-click its header to inspect all column descriptions.

| Example | Where to inspect |
| --- | --- |
| Gender enum and labels | `public.account_settings.gender` |
| Named composite record | `diagram_examples.shipping_address` (`public.contact_address`) |
| Nested composite record | `diagram_examples.contact` (`public.contact_details`) |
| Numeric domain with a check | `diagram_examples.amount` (`public.non_negative_money`) |
| Custom numeric range | `diagram_examples.budget` (`public.price_range`) |
| Built-in timestamp range | `diagram_examples.active_during` (`tstzrange`) |
| Enum/record arrays | `diagram_examples.gender_options` and `previous_addresses` |
| CHECK, IDENTITY, GENERATED | `quantity`, `unit_price`, `sequence_id`, and `line_total` |
| Arrays, JSON, long defaults | `diagram_examples.tags` and `metadata` |
| Primary/unique keys and nullable columns | `diagram_examples.id`, `code`, and `description` |
| Composite, GIN, and partial indexes | The Indexes section of `diagram_examples` details |
| Indexed foreign key and self-reference | `diagram_examples.owner_user_id` and `parent_id` |
| Composite foreign key across schemas | `public.listings` → `catalog.categories` |
| Composite primary key | `public.profile_members` |
| Domain from another schema | `catalog.categories.code` |
| External reference | `account_settings.user_id` → Supabase-managed `auth.users` |

Record types are defined with `CREATE TYPE ... AS (...)`. The ERD shows the
named column type; its attribute names are described in column notes. It does
not expand record attributes into separate tables or editable rows.
See the [PostgreSQL composite-type documentation](https://www.postgresql.org/docs/17/rowtypes.html).

For example, a value of the address type can be constructed as:

```sql
ROW('Bole Road', 'Addis Ababa', 'ET')::public.contact_address
```

The declarations live under `database/public/types/`. The feature-gallery table
is included by `database/public/public.sql`. These files describe desired schema
state; converting an existing database's text gender column requires a migration
that drops its old default/check, casts values to `public.gender`, and restores
the enum default. Refreshing the diagram only reads the SQL files.
