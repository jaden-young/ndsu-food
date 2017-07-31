CREATE TABLE food_item (
        id SERIAL PRIMARY KEY,
        name VARCHAR(100) NOT NULL UNIQUE,
        vegetarian BOOLEAN,
        gluten_free BOOLEAN,
        nuts BOOLEAN
);

CREATE TABLE restaurant (
        id SERIAL PRIMARY KEY,
        name VARCHAR(100) NOT NULL UNIQUE,
        abbreviation VARCHAR(10) NOT NULL
);

CREATE TABLE served_at (
        date DATE NOT NULL,
        category VARCHAR(25) NOT NULL,
        meal VARCHAR(25) NOT NULL,
        food_item_id INT NOT NULL REFERENCES food_item(id),
        restaurant_id INT NOT NULL REFERENCES restaurant(id)
);

INSERT INTO restaurant (name, abbreviation) VALUES
        ('Residence Dining Center', 'RDC'),
        ('West Dining Center', 'WDC'),
        ('Union Dining Center', 'UDC'),
        ('Marketplace Grille', 'MG'),
        ('Pizza Express', 'PE');
