ALTER TABLE served_at ADD CONSTRAINT unique_served_at UNIQUE (date, meal, category, food_item_id, restaurant_id);
