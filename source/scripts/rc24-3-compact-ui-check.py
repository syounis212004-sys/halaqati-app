import sys, re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
k = root/'app/src/main/java/com/mohammedsamir/halaqati'
rd = lambda p: (root/p if not str(p).startswith('app') else root/p).read_text(encoding='utf-8')
errors = []
comp = rd('app/src/main/java/com/mohammedsamir/halaqati/ui/components/Components.kt')
for tok in ('fun CompactSearchField', 'Icons.Default.CloudOff', 'hostError', 'ImageVector? = null'):
    if tok not in comp: errors.append(f'Components.kt missing {tok}')
dash = rd('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/DashboardScreen.kt')
for tok in ('HomeMessages', 'homeIcon', 'import androidx.compose.material.icons.filled.PlayCircle',
            'import androidx.compose.material.icons.filled.MenuBook', 'import androidx.compose.material.icons.filled.TaskAlt',
            'import androidx.compose.material.icons.filled.EmojiEvents', 'import androidx.compose.material.icons.filled.Weekend'):
    if tok not in dash: errors.append(f'DashboardScreen.kt missing {tok}')
if 'كل يوم، أثرٌ يبقى' in dash: errors.append('fixed hero text still in dashboard')
core = rd('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
if core.count('CompactSearchField(') < 2: errors.append('CompactSearchField not used in CoreScreens')
if 'UiDensity.COMPACT' not in rd('app/src/main/java/com/mohammedsamir/halaqati/data/UiPreferencesStore.kt'): errors.append('default density not COMPACT')
if 'ScreenHorizontal = 16.dp' not in rd('app/src/main/java/com/mohammedsamir/halaqati/ui/theme/Theme.kt'): errors.append('RC28 ScreenHorizontal not 16dp')
if not (root/'RELEASE_2_60_3_RC24_3_COMPACT_UI_AR.md').exists(): errors.append('release note missing')
if errors:
    print('\n'.join(errors)); sys.exit(1)
print('RC24.3 compact UI check passed')
