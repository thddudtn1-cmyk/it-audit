// 분기 회차 화면 공통 스크립트 (외부 라이브러리 없음)
(function () {
    'use strict';

    // 제외 체크 시 행을 흐리게 표시하고 사유 입력을 필수로
    document.querySelectorAll('[data-exclude-toggle]').forEach(function (box) {
        var row = box.closest('tr');
        var reason = row.querySelector('input[name$=".excludeReason"]');
        function sync() {
            row.classList.toggle('row--inactive', box.checked);
            if (reason) {
                reason.required = box.checked;
            }
        }
        box.addEventListener('change', sync);
        sync();
    });

    // 배정 편집: 담당팀 선택 시 그 팀 담당자만 보이고, 담당자 선택 시 담당팀 자동 지정
    document.querySelectorAll('[data-assign-row]').forEach(function (row) {
        var team = row.querySelector('[data-team]');
        var assignee = row.querySelector('[data-assignee]');
        if (!team || !assignee) {
            return;
        }
        function filterAssignees() {
            Array.prototype.forEach.call(assignee.options, function (opt) {
                if (!opt.value) {
                    return;
                }
                opt.hidden = !!team.value && opt.getAttribute('data-team') !== team.value;
            });
            var selected = assignee.options[assignee.selectedIndex];
            if (selected && selected.hidden) {
                assignee.value = '';
            }
        }
        team.addEventListener('change', filterAssignees);
        assignee.addEventListener('change', function () {
            var selected = assignee.options[assignee.selectedIndex];
            if (selected && selected.value) {
                team.value = selected.getAttribute('data-team') || '';
                filterAssignees();
            }
        });
        filterAssignees();
    });
})();
