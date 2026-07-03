package io.averkhogliad.ai.challenge.week4.cli.cli.renderers

import io.averkhogliad.ai.challenge.week4.cli.cli.renderers.ConsoleColors.CYAN
import io.averkhogliad.ai.challenge.week4.cli.cli.renderers.ConsoleColors.GREEN
import io.averkhogliad.ai.challenge.week4.cli.cli.renderers.ConsoleColors.RED
import io.averkhogliad.ai.challenge.week4.cli.cli.renderers.ConsoleColors.RESET
import io.averkhogliad.ai.challenge.week4.cli.cli.renderers.ConsoleColors.YELLOW
import io.averkhogliad.ai.challenge.week4.cli.domain.model.Profile

/**
 * Специализированный рендерер для профилей.
 *
 * Выделен из [ConsoleCliRenderer] для соблюдения Single Responsibility Principle.
 * Не зависит от внешних сервисов — легко тестируется (перехват System.out).
 */
class ProfileRenderer {

    fun renderProfileList(profiles: List<Profile>) {
        println()
        if (profiles.isEmpty()) {
            println("${YELLOW}👤 Профили не найдены${RESET}")
        } else {
            val activeProfile = profiles.find { it.isActive }
            println("${CYAN}👤 Профили:${RESET}")
            profiles.forEachIndexed { index, profile ->
                val marker = if (profile.isActive) "${GREEN}*${RESET}" else " "
                println("  ${index + 1}. $marker ${profile.name} (id: ${profile.id.value.take(8)}...)")
            }
            if (activeProfile == null) {
                println("${YELLOW}Активный профиль не задан${RESET}")
            }
        }
        println()
    }

    fun renderProfileDetail(profile: Profile) {
        println()
        println("${CYAN}${"=".repeat(60)}${RESET}")
        println("${CYAN}  👤 Профиль: ${profile.name}${RESET}")
        println("${CYAN}${"=".repeat(60)}${RESET}")
        println("  ${CYAN}ID:${RESET} ${profile.id.value}")
        println("  ${CYAN}Статус:${RESET} ${if (profile.isActive) "${GREEN}АКТИВЕН${RESET}" else "неактивен"}")
        println("  ${CYAN}Создан:${RESET} ${profile.createdAt}")
        println("  ${CYAN}Обновлён:${RESET} ${profile.updatedAt}")
        println("${CYAN}${"-".repeat(60)}${RESET}")
        println("  ${CYAN}Описание:${RESET}")
        println(if (profile.description.isNotEmpty()) profile.description else "${YELLOW}(не задано)${RESET}")
        println("${CYAN}${"-".repeat(60)}${RESET}")
        println("  ${CYAN}Инструкции:${RESET}")
        println(if (profile.instructions.isNotEmpty()) profile.instructions else "${YELLOW}(не задано)${RESET}")
        println("${CYAN}${"-".repeat(60)}${RESET}")
        println()
    }

    fun renderProfileDeleted(name: String) {
        println()
        println("${GREEN}✅ Профиль \"$name\" удалён${RESET}")
        println()
    }

    fun renderProfileUpdated(name: String) {
        println()
        println("${GREEN}✅ Профиль \"$name\" обновлён${RESET}")
        println()
    }

    fun renderProfileError(message: String) {
        println()
        println("${RED}❌ [ОШИБКА] $message${RESET}")
        println()
    }

    fun renderMultilineInputPrompt() {
        println("${CYAN}📝 Введите содержимое профиля (завершите :done, отмените :cancel):${RESET}")
        print("${CYAN}> ${RESET}")
    }

    fun renderProfileDescriptionPrompt() {
        println()
        println("${CYAN}📝 Введите описание профиля (завершите :done, отмените :cancel):${RESET}")
        print("${CYAN}> ${RESET}")
    }

    fun renderProfileInstructionsPrompt() {
        println()
        println("${CYAN}📝 Введите инструкции профиля (завершите :done, отмените :cancel):${RESET}")
        print("${CYAN}> ${RESET}")
    }

    fun renderProfileNotFoundById(id: String) {
        println()
        println("${RED}❌ Профиль с ID \"$id\" не найден${RESET}")
        println()
    }

    fun renderProfileNotFoundByName(name: String) {
        println()
        println("${RED}❌ Профиль с именем \"$name\" не найден${RESET}")
        println()
    }

    fun renderProfileAlreadyExists(name: String) {
        println()
        println("${RED}❌ Профиль с названием \"$name\" уже существует${RESET}")
        println()
    }

    fun renderMissingProfileId() {
        println()
        println("${RED}❌ Укажите ID профиля. Использование: :profile-activate <id>${RESET}")
        println()
    }

    fun renderMissingProfileName() {
        println()
        println("${RED}❌ Укажите имя профиля. Использование: :profile-create <name>${RESET}")
        println()
    }

    fun renderEmptyProfileContent() {
        println()
        println("${RED}❌ Содержимое профиля не может быть пустым${RESET}")
        println()
    }

    fun renderCannotDeleteActiveProfile() {
        println()
        println("${RED}❌ Нельзя удалить активный профиль. Сначала переключитесь на другой профиль.${RESET}")
        println()
    }

    fun renderProfileContentTooLong(length: Int) {
        println()
        println("${RED}❌ Содержимое профиля не может превышать 1000 символов (текущая длина: $length)${RESET}")
        println()
    }

    fun renderStatusProfile(profileName: String?) {
        println()
        if (profileName != null) {
            println("${CYAN}👤 Активный профиль: \"$profileName\"${RESET}")
        } else {
            println("${YELLOW}👤 Профиль не задан${RESET}")
        }
        println()
    }
}
