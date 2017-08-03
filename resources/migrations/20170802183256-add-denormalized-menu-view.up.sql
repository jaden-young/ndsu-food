CREATE VIEW all_menus AS
SELECT fi.id AS food_item_id, fi.name AS food_item_name,
       fi.vegetarian, fi.gluten_free, fi.nuts,
       sa.date, sa.meal, sa.category,
       r.id AS restaurant_id, r.abbreviation
FROM food_item as fi
    JOIN served_at as sa
    ON fi.id = sa.food_item_id
    JOIN restaurant AS r
    on r.id = sa.restaurant_id;
