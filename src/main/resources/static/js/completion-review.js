// 개선사항 완료 검토: 선택한 상태에 따라 코멘트 필수 여부와 안내를 바꾼다 (외부 라이브러리 없음)
(function () {
    'use strict';
    var form = document.querySelector('[data-completion-review]');
    if (!form) {
        return;
    }
    var select = form.querySelector('select[name="status"]');
    var comment = form.querySelector('textarea[name="comment"]');
    var label = form.querySelector('[data-comment-label]');

    function sync() {
        var back = select.value === 'IN_PROGRESS' || select.value === 'PLANNED';
        comment.required = back;
        label.textContent = back ? '반려 사유 (필수 · 담당자에게 보입니다)'
            : select.value === 'CLOSED' ? '최종확정 의견 (선택)' : '코멘트';
    }

    select.addEventListener('change', sync);
    form.addEventListener('submit', function (e) {
        if (select.value === 'CLOSED' && !window.confirm('최종확정하면 더 이상 수정할 수 없습니다. 계속할까요?')) {
            e.preventDefault();
        }
    });
    sync();
})();
