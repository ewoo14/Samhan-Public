-- 싱글 자재가격(MaterialPrice) lookup 을 Product(MATERIAL) 품목으로 1회 편입한다.
-- 원천 값은 기존 material_price D2-D29 활성 행이며, 이후 단가 원천은 품목 등록/관리 화면의 products 이다.

-- fresh DB(material_price 비어있음·CI Testcontainers·신규 배포)에서도 28 자재가 결정적으로
-- 시드되도록 값을 직접 박제한다. (material_price 는 마이그로 시드되지 않고 런타임 시트 sync 로만
-- 채워지므로 복사 방식은 fresh DB 에서 0건이 됨 — 2026-06-16 운영 스냅샷 28행 박제로 대체.)
-- 이후 단가 원천은 products(품목 등록/관리 화면). material_price 복사 아님.
WITH source_rows AS (
    SELECT
        v.name,
        v.price,
        upper(substr(md5('samhan-product-material-code:' || trim(v.name)), 1, 12)) AS code_hash,
        md5('samhan-product-material-id:' || trim(v.name)) AS id_hash
      FROM (VALUES
        ('유선리모컨', 40000),
        ('컬러유선리모컨', 75000),
        ('블랙판넬', 50000),
        ('승강판넬', 60000),
        ('공청판넬', 550000),
        ('1WAY 중형 공청', 215000),
        ('1WAY 대형 공청', 260000),
        ('FPH-1412XS3', 130000),
        ('FPH-1458XS1', 130000),
        ('FPH-3858XS5', 180000),
        ('FPH-3878XS', 360000),
        ('FPC-3858XS2', 180000),
        ('FRH-1412NA3', 60000),
        ('FRH-1438NH3', 50000),
        ('FRH-1412XA3', 60000),
        ('FRC-1438NB2', 50000),
        ('FRC-1438NA2', 50000),
        ('FRC-1412NA2', 60000),
        ('FRC-1458XA2', 60000),
        ('FPC-1458YAF2', 100000),
        ('FPC-1412YAF2', 100000),
        ('FRC-1438XAF2', 50000),
        ('AFR-TC9D', 47000),
        ('AFR-QC3F', 25000),
        ('AFR-BC3F', 34000),
        ('ARR-WK8F', 34000),
        ('ARR-NK3F', 25000),
        ('ARR-PK8F', 34000)
      ) AS v(name, price)
),
material_products AS (
    SELECT
        (substr(id_hash, 1, 8) || '-' ||
         substr(id_hash, 9, 4) || '-' ||
         substr(id_hash, 13, 4) || '-' ||
         substr(id_hash, 17, 4) || '-' ||
         substr(id_hash, 21, 12))::uuid AS product_id,
        name,
        'MAT-' || code_hash AS model_code,
        price
      FROM source_rows
)
INSERT INTO products (
    id,
    name,
    model_name,
    category_id,
    selling_price,
    purchase_price,
    currency,
    status,
    model_code,
    product_code,
    product_type,
    release_price,
    delivery_price,
    product_category,
    usage_scope,
    estimate_category,
    unit,
    inventory_qty_mgmt,
    product_group1,
    product_group2,
    goods_type,
    created_at,
    created_by,
    is_deleted
)
SELECT
    mp.product_id,
    mp.name,
    mp.model_code,
    COALESCE(
        (SELECT c.id FROM categories c WHERE c.code = 'PIPING' AND c.is_deleted = FALSE LIMIT 1),
        '00000000-0000-0000-0000-000000001006'::uuid
    ),
    mp.price,
    0,
    'KRW',
    'ACTIVE',
    mp.model_code,
    mp.model_code,
    'SINGLE',
    mp.price,
    mp.price,
    'MATERIAL',
    'NONE',
    NULL,
    'EA',
    FALSE,
    'SINGLE_MATERIAL',
    'MATERIAL',
    'NON_GOODS',
    NOW(),
    'system-material-product-seed',
    FALSE
  FROM material_products mp
 WHERE NOT EXISTS (
        SELECT 1
          FROM products p
         WHERE p.is_deleted = FALSE
           AND (
                p.model_code = mp.model_code
                OR p.product_code = mp.model_code
                OR (p.product_category = 'MATERIAL' AND p.name = mp.name)
           )
    );
