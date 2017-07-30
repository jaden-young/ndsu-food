-- :name create-food-item! :i
-- :doc creates a new food item, returning the auto-generated id
INSERT INTO food_item
(name, vegetarian, gluten_free, nuts)
VALUES (:name, :vegetarian, :gluten_free, :nuts)
RETURNING id

-- :name all-food-items :? :*
-- :doc gets every food item
SELECT id, name, vegetarian, gluten_free, nuts
FROM food_item

-- :name food-item :? :1
-- :doc get a single food-item by id
SELECT id, name, vegetarian, gluten_free, nuts
FROM food_item
WHERE id = :id

-- :name food-item-by-name :? :1
-- :doc get a single food item with a matching name
SELECT id, name, vegetarian, gluten_free, nuts
FROM food_item
WHERE name = :name

-- :name all-restaurants :? :*
-- :doc gets every restaurant
SELECT id, name, abbreviation
FROM restaurant

-- :name restaurant :? :1
-- :doc gets a single restaurant by id
SELECT id, name, abbreviation
FROM restaurant
WHERE id = :id

-- :name restaurant-by-name :? :1
-- :doc gets a single restaurant with matching name
SELECT id, name, abbreviation
FROM restaurant
WHERE name = :name

-- :name restaurant-by-abbreviation :? :1
-- :doc gets a single restaurant with matching abbreviation
SELECT id, name, abbreviation
FROM restaurant
WHERE abbreviation = :abbreviation

-- :name served-on-date :? :*
-- :doc gets all items served on a given date
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

-- :name insert-menu-data! :! :n
-- :doc inserts menu data
INSERT INTO served_at (date, meal, category, food_item_id, restaurant_id)
VALUES (:date, :meal, :category, :food_item_id, :restaurant_id)
