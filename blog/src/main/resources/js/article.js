const deleteButton = document.getElementById('delete-btn');

if (deleteButton) {
    deleteButton.addEventListener('click', () => {
        const idField = document.getElementById('article-id');
        const id = idField ? idField.value : null;
        if (!id) {
            return;
        }
        fetch(`/api/articles/${id}`, {method: 'DELETE'})
            .then(() => {
                alert('Article deleted successfully');
                location.replace('/articles');
            })
    })
}

const modifyButton = document.getElementById('modify-btn');
const titleInput = document.getElementById('title');
const contentInput = document.getElementById('content');

if (modifyButton && titleInput && contentInput) {
    modifyButton.addEventListener('click', event => {
        event.preventDefault();
        const params = new URLSearchParams(location.search);
        const idField = document.getElementById('article-id');
        const id = (idField && idField.value) ? idField.value : params.get('id');
        if (!id) {
            return;
        }

        fetch(`/api/articles/${id}`, {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},

                body: JSON.stringify({
                    title: titleInput.value,
                    content: contentInput.value
                })
            })
            .then(() => {
            alert('Article modified successfully');
            location.replace(`/articles/${id}`);
        })
    })
}

const createButton = document.getElementById('save-btn');

if (createButton && titleInput && contentInput) {
    createButton.addEventListener('click', event => {
        event.preventDefault();
        fetch('/api/articles', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                title: titleInput.value,
                content: contentInput.value
            })
        }).then(() => {
            alert('Article created successfully');
            location.replace('/articles');
        })
    })
}
