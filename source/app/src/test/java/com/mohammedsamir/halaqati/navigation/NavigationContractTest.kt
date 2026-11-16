package com.mohammedsamir.halaqati.navigation
import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.ui.navigation.NavigationContract
import org.junit.Assert.*
import org.junit.Test
class NavigationContractTest {
 @Test fun competitionAndAchievementsResolveToDifferentDestinations(){ assertEquals("guardian_competition",NavigationContract.resolve(UserRole.GUARDIAN,"open_competition")?.route); assertEquals("guardian_achievements",NavigationContract.resolve(UserRole.GUARDIAN,"open_achievements")?.route); assertNotEquals(NavigationContract.resolve(UserRole.GUARDIAN,"open_competition"),NavigationContract.resolve(UserRole.GUARDIAN,"open_achievements")) }
 @Test fun accountsActionCannotResolveToImportExport(){ assertEquals("users",NavigationContract.resolve(UserRole.ADMIN,"open_users")?.route); assertNotEquals("importexport",NavigationContract.resolve(UserRole.ADMIN,"open_users")?.route) }
 @Test fun forbiddenActionFailsClosed(){ assertNull(NavigationContract.resolve(UserRole.GUARDIAN,"open_users")) }
}
