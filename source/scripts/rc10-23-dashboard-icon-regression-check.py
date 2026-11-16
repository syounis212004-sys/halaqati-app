#!/usr/bin/env python3
from pathlib import Path
p = Path('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/DashboardScreen.kt')
text = p.read_text(encoding='utf-8')
usage = 'Icons.Default.PersonSearch'
imp = 'import androidx.compose.material.icons.filled.PersonSearch'
if usage in text and imp not in text:
    raise SystemExit('FAIL: Dashboard uses PersonSearch without importing material-icons filled.PersonSearch')
print('RC10.2/10.3 dashboard icon import regression passed')
