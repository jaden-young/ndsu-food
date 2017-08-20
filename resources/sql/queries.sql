-- :name get-food-item-by-id :? :1
-- :doc Gets a single food-item by :id. Optionally {:cols ["col1", ...]}
SELECT
--~ (if (seq (get params :cols)) ":i*:cols" "*")
FROM food_item
WHERE id = :id

-- :name get-food-item-by-name :? :1
-- :doc Gets a single food item with a matching :name. Optionally {:cols ["col1", ...]}
SELECT
--~ (if (seq (get params :cols)) ":i*:cols" "*")
FROM food_item
WHERE name = :name

-- :name get-food-items :? :*
-- :doc Gets all food-items. Optionally specify {:cols ["col1", "col2", ...]}
SELECT
--~ (if (seq (get params :cols)) ":i*:cols" "*")
FROM food_item
ORDER BY id

-- :name new-food-item! :i
-- :doc Inserts a new food item, returning the auto-generated id.
INSERT INTO food_item
(name, vegetarian, gluten_free, nuts)
VALUES (:name, :vegetarian, :gluten_free, :nuts)
RETURNING id

-- :name get-restaurant-by-id :? :1
-- :doc Gets a single restaurant by :id. Optionally {:cols ["col1", "col2", ...]}
SELECT id, name, abbreviation
--~ (if (seq (get params :cols)) ":i*:cols" "*")
FROM restaurant
WHERE id = :id

-- :name get-restaurant-by-name :? :1
-- :doc Gets a single restaurant with matching :name. Optionally {:cols ["col1", "col2", ...]}
SELECT id, name, abbreviation
--~ (if (seq (get params :cols)) ":i*:cols" "*")
FROM restaurant
WHERE name = :name

-- :name get-restaurant-by-abbreviation :? :1
-- :doc Gets a single restaurant with matching :abbreviation. Optionally {:cols ["col1", "col2", ...]}
SELECT
--~ (if (seq (get params :cols)) ":i*:cols" "*")
FROM restaurant
WHERE abbreviation = :abbreviation
ORDER BY id

-- :name get-restaurants :? :*
-- :doc Gets all restaurants. Optionally specify {:cols ["col1", "col2", ...]}
SELECT
--~ (if (seq (get params :cols)) ":i*:cols" "*")
FROM restaurant
ORDER BY id

-- :name served-on-date :? :*
-- :doc Gets menu data (only ids) for a given :date. Optionally specify {:cols ["col1", ...]}
SELECT
--~ (if (seq (get params :cols)) ":i*:cols" "*")
FROM served_at
WHERE date = :date

-- :name get-menu-on-date :? :*
-- :doc Use {:date date (optionally):cols ["col1", "col2", ...]}
SELECT
--~ (if (seq (get params :cols)) ":i*:cols" "*")
FROM all_menus
WHERE date = :date

-- :name insert-menu-data-with-ids! :! :n
-- :doc Inserts menu data, expects :date :meal :category :food_item_id :restaurant_id
INSERT INTO served_at (date, meal, category, food_item_id, restaurant_id)
VALUES (:date, :meal, :category, :food_item_id, :restaurant_id)

-- :name new-food-item-served-at! :! :n
-- :doc Inserts the food item and associated menu data, expects :food_item_name :vegetarian :gluten_free :nuts :date :meal :category :restaurant_name
-- Inserting restaurant data isn't supported as that would realistically be a
-- waste of work. The set of restaurants is already fully populated in the db
-- in the initial schema. If more are to be supported, it will be done with
-- migrations.
WITH fi_s AS (
    SELECT id
    FROM food_item
    WHERE name = :food_item_name
), fi_i AS (
    INSERT INTO food_item (name, vegetarian, gluten_free, nuts)
    SELECT :food_item_name, :vegetarian, :gluten_free, :nuts
    WHERE NOT EXISTS (SELECT 1 FROM fi_s)
    RETURNING id
), fi AS (
    -- only one of these will have an element.
    SELECT id
    FROM fi_s
    UNION ALL
    SELECT id
    FROM fi_i
), r AS (
    SELECT id
    FROM restaurant
    WHERE name = :restaurant_name
 )
INSERT INTO served_at (date, meal, category, food_item_id, restaurant_id)
VALUES (:date, :meal, :category, (SELECT id FROM fi), (SELECT id FROM r))
ON CONFLICT DO NOTHING
