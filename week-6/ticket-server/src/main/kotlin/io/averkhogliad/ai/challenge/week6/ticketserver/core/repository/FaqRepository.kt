package io.averkhogliad.ai.challenge.week6.ticketserver.core.repository

import io.averkhogliad.ai.challenge.week6.ticketserver.core.model.FaqArticle
import org.springframework.stereotype.Repository

@Repository
class FaqRepository {

    private val articles: List<FaqArticle>

    init {
        articles = seedData()
    }

    fun findAll(): List<FaqArticle> = articles

    fun search(query: String): List<FaqArticle> {
        val lowerQuery = query.lowercase()
        return articles.filter { article ->
            article.title.lowercase().contains(lowerQuery) ||
                    article.content.lowercase().contains(lowerQuery) ||
                    article.tags.any { it.lowercase().contains(lowerQuery) }
        }
    }

    private fun seedData(): List<FaqArticle> = listOf(
        FaqArticle(
            id = "FAQ-001",
            title = "Как восстановить пароль?",
            content = "Перейдите на страницу входа и нажмите «Забыли пароль». На вашу почту придёт ссылка для сброса. Ссылка действительна 30 минут. Если письмо не пришло, проверьте папку «Спам».",
            tags = listOf("авторизация", "пароль", "вход"),
        ),
        FaqArticle(
            id = "FAQ-002",
            title = "Почему списали деньги дважды?",
            content = "Двойное списание может произойти при техническом сбое платёжного шлюза. Обратитесь в поддержку через тикет с пометкой «CRITICAL», приложив скриншоты транзакций. Возврат производится в течение 3-5 рабочих дней.",
            tags = listOf("оплата", "подписка", "списание", "возврат"),
        ),
        FaqArticle(
            id = "FAQ-003",
            title = "Как экспортировать отчёт?",
            content = "В разделе «Аналитика» выберите период и нажмите «Экспорт». Доступны форматы CSV и PDF. При проблемах с PDF-экспортом попробуйте CSV — эта функция работает стабильнее.",
            tags = listOf("экспорт", "отчёт", "PDF", "CSV"),
        ),
        FaqArticle(
            id = "FAQ-004",
            title = "Тарифные планы и лимиты",
            content = "Basic: до 5 проектов, 1 ГБ хранилища. Pro: до 50 проектов, 10 ГБ хранилища, расширенная аналитика. Enterprise: безлимитно, персональный менеджер. Сменить тариф можно в разделе «Настройки» → «Подписка».",
            tags = listOf("тариф", "подписка", "лимиты", "Basic", "Pro", "Enterprise"),
        ),
        FaqArticle(
            id = "FAQ-005",
            title = "Не загружается страница после входа",
            content = "Очистите кеш браузера (Ctrl+Shift+Del) и удалите куки для нашего сайта. Попробуйте войти в режиме инкогнито. Если проблема сохраняется, отключите расширения браузера, особенно блокировщики рекламы.",
            tags = listOf("авторизация", "вход", "браузер", "кеш"),
        ),
        FaqArticle(
            id = "FAQ-006",
            title = "Где посмотреть историю изменений?",
            content = "История изменений доступна в разделе «Аудит» для тарифов Pro и Enterprise. Каждое изменение содержит дату, автора и описание. Логи хранятся 90 дней.",
            tags = listOf("аудит", "история", "изменения", "логи"),
        ),
        FaqArticle(
            id = "FAQ-007",
            title = "Как работает реферальная программа?",
            content = "Пригласите друга по реферальной ссылке из раздела «Настройки» → «Рефералы». Вы получите месяц Pro бесплатно, а ваш друг — скидку 20% на первый платёж. Начисления происходят после первой оплаты приглашённого.",
            tags = listOf("рефералы", "скидка", "бонус"),
        ),
        FaqArticle(
            id = "FAQ-008",
            title = "Почему статистика не обновляется?",
            content = "Статистика обновляется раз в час для Basic и раз в 15 минут для Pro и Enterprise. Если данные отсутствуют, проверьте, что период выбран корректно и за указанные даты есть активность.",
            tags = listOf("статистика", "аналитика", "обновление", "график"),
        ),
    )
}
