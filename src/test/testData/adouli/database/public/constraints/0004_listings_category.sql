ALTER TABLE public.listings
    ADD CONSTRAINT listings_category_same_profile_fk
    FOREIGN KEY (category_id, profile_id)
    REFERENCES catalog.categories (id, profile_id) ON DELETE RESTRICT;
