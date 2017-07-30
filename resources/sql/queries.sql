-- :name create-food-item! :i
-- :doc Creates a new food item, returning the auto-generated id
INSERT INTO food_item
(name, vegetarian, gluten_free, nuts)
VALUES (:name, :vegetarian, :gluten-free, :nuts)
RETURNING id

-- :name all-food-items :? :*
-- :doc Gets every food item
SELECT id, name, vegetarian, gluten_free, nuts
FROM food_item

-- :name food-item :? :1
-- :doc Gets a single food-item by :id
SELECT id, name, vegetarian, gluten_free, nuts
FROM food_item
WHERE id = :id

-- :name food-item-by-name :? :1
-- :doc Gets a single food item with a matching :name
SELECT id, name, vegetarian, gluten_free, nuts
FROM food_item
WHERE name = :name

-- :name all-restaurants :? :*
-- :doc Gets every restaurant
SELECT id, name, abbreviation
FROM restaurant

-- :name restaurant :? :1
-- :doc Gets a single restaurant by :id
SELECT id, name, abbreviation
FROM restaurant
WHERE id = :id

-- :name restaurant-by-name :? :1
-- :doc Gets a single restaurant with matching :name
SELECT id, name, abbreviation
FROM restaurant
WHERE name = :name

-- :name restaurant-by-abbreviation :? :1
-- :doc Gets a single restaurant with matching :abbreviation
SELECT id, name, abbreviation
FROM restaurant
WHERE abbreviation = :abbreviation

-- :name served-on-date :? :*
-- :doc Gets all food items served on a given :date
SELECT
    fi.name AS food_item_name,
    fi.vegetarian, fi.gluten_free, fi.nuts,
    r.name AS restaurant_name,
    sa.date, sa.meal AS meal, sa.category AS category
FROM
    food_item AS fi
      JOIN served_at AS sa
          ON fi.id = sa.food_item_id
      JOIN restaurant AS r
          ON r.id = sa.restaurant_id
WHERE
    sa.date = :date
ORDER BY
    meal, r.name, category

-- :name insert-menu-data-with-ids! :! :n
-- :doc Inserts menu data, expects :date :meal :category :food-item-id :restaurant-id
INSERT INTO served_at (date, meal, category, food_item_id, restaurant_id)
VALUES (:date, :meal, :category, :food-item-id, :restaurant-id)

-- :name- insert-menu-data-all! :! :n
-- :doc Inserts the food-item and associated menu, expects :food-item-name :vegetarian :gluten-free :nuts :date :meal :category :restaurant-name
-- Inserting restaurant data isn't supported as that would realistically be a
-- waste of work. The set of restaurants is already fully populated in the db
-- in the initial schema. If more are to be supported, it will be done with
-- migrations.
WITH ins_fi AS (
  INSERT INTO food_item (name, vegetarian, gluten_free, nuts)
  VALUES (:food-item-name, :vegetarian, :gluten-free, :nuts)
  ON CONFLICT DO NOTHING
  RETURNING id
  )
, r_id AS (
      SELECT id
      FROM restaurant
      WHERE name = :restaurant-name
 )
INSERT INTO served_at (date, meal, category, food_item_id, restaurant_id)
VALUES (:date, :meal, :category, (SELECT id FROM ins_fi), (SELECT id FROM r_id))
