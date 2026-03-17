-- Seed data for local RAG verification.
-- This script is idempotent and can be executed repeatedly.

INSERT INTO media (title, original_title, type, status, release_date, duration, summary, rating, deleted)
SELECT '星际穿越', 'Interstellar', 1, 2, '2014-11-12', 169,
       '一组探险者穿越虫洞寻找人类新家园，围绕亲情、时间膨胀和生存抉择展开。', 9.4, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM media WHERE title = '星际穿越' AND deleted = 0);

INSERT INTO media (title, original_title, type, status, release_date, duration, summary, rating, deleted)
SELECT '盗梦空间', 'Inception', 1, 2, '2010-09-01', 148,
       '一支团队潜入他人梦境执行植入任务，现实与梦境层层嵌套。', 9.2, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM media WHERE title = '盗梦空间' AND deleted = 0);

INSERT INTO media (title, original_title, type, status, release_date, duration, summary, rating, deleted)
SELECT '霸王别姬', 'Farewell My Concubine', 1, 2, '1993-01-01', 171,
       '以京剧人生映照时代巨变，讲述程蝶衣与段小楼半生沉浮。', 9.6, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM media WHERE title = '霸王别姬' AND deleted = 0);

INSERT INTO media (title, original_title, type, status, release_date, duration, summary, rating, deleted)
SELECT '千与千寻', 'Spirited Away', 3, 2, '2001-07-20', 125,
       '少女误入神灵世界，在成长与告别中找回名字与勇气。', 9.4, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM media WHERE title = '千与千寻' AND deleted = 0);

INSERT INTO media (title, original_title, type, status, release_date, duration, summary, rating, deleted)
SELECT '让子弹飞', 'Let the Bullets Fly', 1, 2, '2010-12-16', 132,
       '民国乱世中假县长与土匪、豪绅角力，黑色幽默与权力博弈并行。', 8.9, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM media WHERE title = '让子弹飞' AND deleted = 0);

INSERT INTO media (title, original_title, type, status, release_date, duration, summary, rating, deleted)
SELECT '流浪地球', 'The Wandering Earth', 1, 2, '2019-02-05', 125,
       '太阳危机逼近，人类启动行星发动机推动地球远航。', 8.0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM media WHERE title = '流浪地球' AND deleted = 0);

INSERT INTO media (title, original_title, type, status, release_date, duration, summary, rating, deleted)
SELECT '我不是药神', 'Dying to Survive', 1, 2, '2018-07-05', 117,
       '小人物卷入抗癌药代购风波，在现实困境中完成自我蜕变。', 9.1, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM media WHERE title = '我不是药神' AND deleted = 0);

INSERT INTO media (title, original_title, type, status, release_date, duration, summary, rating, deleted)
SELECT '疯狂动物城', 'Zootopia', 3, 2, '2016-03-04', 109,
       '兔警官与狐搭档调查失踪案，揭示偏见背后的制度问题。', 9.2, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM media WHERE title = '疯狂动物城' AND deleted = 0);

INSERT INTO media (title, original_title, type, status, release_date, duration, summary, rating, deleted)
SELECT '琅琊榜', 'Nirvana in Fire', 2, 2, '2015-09-19', 45,
       '梅长苏重返金陵，以谋略清算旧案并扶持明主。', 9.4, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM media WHERE title = '琅琊榜' AND deleted = 0);

INSERT INTO media (title, original_title, type, status, release_date, duration, summary, rating, deleted)
SELECT '漫长的季节', 'The Long Season', 2, 2, '2023-04-22', 50,
       '东北小城跨越多年的案件纠缠，拼出时代与个体命运。', 9.4, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM media WHERE title = '漫长的季节' AND deleted = 0);

INSERT INTO media (title, original_title, type, status, release_date, duration, summary, rating, deleted)
SELECT '绝命毒师', 'Breaking Bad', 2, 2, '2008-01-20', 47,
       '化学老师在绝境中走向犯罪深渊，家庭与道德全面崩塌。', 9.7, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM media WHERE title = '绝命毒师' AND deleted = 0);

INSERT INTO media (title, original_title, type, status, release_date, duration, summary, rating, deleted)
SELECT '黑镜', 'Black Mirror', 2, 2, '2011-12-04', 60,
       '独立单元故事聚焦技术与人性冲突，展现近未来社会风险。', 8.8, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM media WHERE title = '黑镜' AND deleted = 0);

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1001, '科学细节和情感线都很扎实，结尾的维度设定很震撼。', 9.5, 286, 1, NOW()
FROM media m
WHERE m.title = '星际穿越'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '科学细节和情感线都很扎实，结尾的维度设定很震撼。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1002, '前段铺垫略慢，但中后段节奏和配乐把情绪完全拉满。', 9.1, 173, 1, NOW()
FROM media m
WHERE m.title = '星际穿越'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '前段铺垫略慢，但中后段节奏和配乐把情绪完全拉满。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1003, '梦境规则清晰，动作场面和叙事结构非常工整。', 9.2, 212, 1, NOW()
FROM media m
WHERE m.title = '盗梦空间'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '梦境规则清晰，动作场面和叙事结构非常工整。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1004, '多刷后会发现每一层梦境都埋了回扣，完成度很高。', 9.0, 166, 1, NOW()
FROM media m
WHERE m.title = '盗梦空间'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '多刷后会发现每一层梦境都埋了回扣，完成度很高。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1005, '人物命运和时代叙事高度统一，表演具有历史厚重感。', 9.7, 258, 1, NOW()
FROM media m
WHERE m.title = '霸王别姬'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '人物命运和时代叙事高度统一，表演具有历史厚重感。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1006, '程蝶衣这一角色的悲剧性极强，后劲非常大。', 9.5, 149, 1, NOW()
FROM media m
WHERE m.title = '霸王别姬'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '程蝶衣这一角色的悲剧性极强，后劲非常大。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1007, '世界观想象力惊人，主题是成长和告别而不只是奇幻冒险。', 9.5, 241, 1, NOW()
FROM media m
WHERE m.title = '千与千寻'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '世界观想象力惊人，主题是成长和告别而不只是奇幻冒险。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1008, '配乐和画面搭配太舒服，很多细节值得反复看。', 9.2, 132, 1, NOW()
FROM media m
WHERE m.title = '千与千寻'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '配乐和画面搭配太舒服，很多细节值得反复看。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1009, '台词密度和讽刺力度都很高，群像关系写得很妙。', 9.1, 198, 1, NOW()
FROM media m
WHERE m.title = '让子弹飞'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '台词密度和讽刺力度都很高，群像关系写得很妙。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1010, '叙事张力很足，笑点背后都是权力结构。', 8.8, 121, 1, NOW()
FROM media m
WHERE m.title = '让子弹飞'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '叙事张力很足，笑点背后都是权力结构。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1011, '工业化完成度很高，灾难场面和家国情绪都到位。', 8.3, 177, 1, NOW()
FROM media m
WHERE m.title = '流浪地球'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '工业化完成度很高，灾难场面和家国情绪都到位。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1012, '前半段调度很紧，后半段情感落点比较稳。', 7.9, 102, 1, NOW()
FROM media m
WHERE m.title = '流浪地球'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '前半段调度很紧，后半段情感落点比较稳。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1013, '现实议题切入有力，人物选择很有说服力。', 9.3, 230, 1, NOW()
FROM media m
WHERE m.title = '我不是药神'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '现实议题切入有力，人物选择很有说服力。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1014, '喜剧外壳下是沉重现实，情绪递进自然。', 9.0, 141, 1, NOW()
FROM media m
WHERE m.title = '我不是药神'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '喜剧外壳下是沉重现实，情绪递进自然。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1015, '案件主线清晰，关于偏见的表达适合全年龄观看。', 9.1, 163, 1, NOW()
FROM media m
WHERE m.title = '疯狂动物城'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '案件主线清晰，关于偏见的表达适合全年龄观看。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1016, '角色塑造很讨喜，笑点密集但不低幼。', 8.9, 115, 1, NOW()
FROM media m
WHERE m.title = '疯狂动物城'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '角色塑造很讨喜，笑点密集但不低幼。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1017, '朝堂权谋线非常扎实，台词克制且有力量。', 9.5, 222, 1, NOW()
FROM media m
WHERE m.title = '琅琊榜'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '朝堂权谋线非常扎实，台词克制且有力量。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1018, '人物弧线完整，配角群像也很出彩。', 9.2, 119, 1, NOW()
FROM media m
WHERE m.title = '琅琊榜'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '人物弧线完整，配角群像也很出彩。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1019, '三条时间线交织很稳，东北气质和悬疑感都在线。', 9.5, 184, 1, NOW()
FROM media m
WHERE m.title = '漫长的季节'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '三条时间线交织很稳，东北气质和悬疑感都在线。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1020, '人物命运的宿命感很强，结尾处理克制。', 9.1, 97, 1, NOW()
FROM media m
WHERE m.title = '漫长的季节'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '人物命运的宿命感很强，结尾处理克制。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1021, '角色塑造层层推进，反英雄叙事完成度极高。', 9.6, 243, 1, NOW()
FROM media m
WHERE m.title = '绝命毒师'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '角色塑造层层推进，反英雄叙事完成度极高。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1022, '每一季冲突都在升级，但人物动机一直自洽。', 9.4, 136, 1, NOW()
FROM media m
WHERE m.title = '绝命毒师'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '每一季冲突都在升级，但人物动机一直自洽。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1023, '单元故事里对科技伦理的讨论很有现实映照。', 8.9, 126, 1, NOW()
FROM media m
WHERE m.title = '黑镜'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '单元故事里对科技伦理的讨论很有现实映照。');

INSERT INTO comment (media_id, user_id, content, rating, like_count, audit_status, create_time)
SELECT m.id, 1024, '虽然质量有波动，但高分集的后劲很足。', 8.6, 88, 1, NOW()
FROM media m
WHERE m.title = '黑镜'
  AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.media_id = m.id AND c.content = '虽然质量有波动，但高分集的后劲很足。');
