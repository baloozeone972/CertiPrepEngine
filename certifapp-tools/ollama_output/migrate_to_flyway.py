Voici un script Python qui répond à vos besoins. Ce script suppose que tous[4D[K
tous les fichiers JSON sont valides et qu'ils contiennent uniquement des qu[2D[K
questions et des configurations de certification corrects. Il ne gère pas l[1D[K
les erreurs possibles.

```python
import argparse
import json
from pathlib import Path
import uuid

NAMESPACE = uuid.uuid4()

def load_config(path):
    with open(path, 'r') as f:
        return json.load(f)

def get_all_files(directory, extension='json'):
    path = Path('.') / directory
    return [file for file in path.glob(f"*.{extension}")]

def generate_sql_statement(table, columns, values):
    insert_query = f