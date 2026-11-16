from pathlib import Path
import sys
p=Path('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
s=p.read_text()
errors=[]
block=s[s.index('fun linkPrimaryGuardian'):s.index('fun reportRecitations')]
if 'queued(' not in block or '"/rest/v1/rpc/set_student_primary_guardian"' not in block:
    errors.append('guardian link must use durable queue so new-student create is uploaded first')
if 'dedupe = "student-guardian:$studentId"' not in block:
    errors.append('guardian link must dedupe by student, not guardian selection')
primary=s[s.index('fun primaryGuardianId'):s.index('fun linkPrimaryGuardian')]
if 'pendingPrimaryGuardianId' not in primary:
    errors.append('primaryGuardianId must overlay pending guardian relation for local-first UI')
if errors:
    print('RC10.2/10.3 guardian queue regression FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('RC10.2/10.3 guardian queue regression passed')
