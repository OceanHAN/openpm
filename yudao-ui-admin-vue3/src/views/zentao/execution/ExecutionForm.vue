<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="700">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="执行与项目共用一张表"
      description="禅道的执行（迭代/阶段/看板）和项目存在同一个 zt_project 表中，靠 type 字段区分。执行必须归属到某个项目下。"
    />
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="100px">
      <el-row>
        <el-col :span="12">
          <el-form-item label="所属项目" prop="project">
            <el-select v-model="formData.project" placeholder="请选择项目" filterable class="w-full">
              <el-option v-for="p in projectList" :key="p.id" :label="p.name" :value="p.id!" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="执行类型" prop="type">
            <el-select v-model="formData.type" placeholder="请选择类型" class="w-full">
              <el-option v-for="o in EXECUTION_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="14">
          <el-form-item label="执行名称" prop="name">
            <el-input v-model="formData.name" placeholder="如 第 1 迭代" maxlength="90" />
          </el-form-item>
        </el-col>
        <el-col :span="10">
          <el-form-item label="优先级" prop="pri">
            <el-select v-model="formData.pri" class="w-full">
              <el-option v-for="o in PRI_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="计划开始" prop="begin">
            <el-date-picker v-model="formData.begin" type="date" value-format="YYYY-MM-DD" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="计划结束" prop="end">
            <el-date-picker v-model="formData.end" type="date" value-format="YYYY-MM-DD" class="w-full" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="预计工时" prop="estimate">
            <el-input-number v-model="formData.estimate" :min="0" :precision="2" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="项目经理" prop="PM">
            <el-input v-model="formData.PM" placeholder="账号" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="团队成员" prop="team">
        <el-input v-model="formData.team" placeholder="多个账号用逗号分隔" />
      </el-form-item>

      <el-form-item label="执行描述" prop="desc">
        <el-input v-model="formData.desc" type="textarea" :rows="3" placeholder="请输入执行描述" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as ExecutionApi from '@/api/zentao/execution'
import * as ProjectApi from '@/api/zentao/project'
import { EXECUTION_TYPE_OPTIONS, PRI_OPTIONS } from './constants'

defineOptions({ name: 'ZentaoExecutionForm' })

const message = useMessage()
const { t } = useI18n()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formRef = ref()
const projectList = ref<ProjectApi.ProjectVO[]>([])

const formData = ref<ExecutionApi.ExecutionVO>({})

const formRules = reactive({
  project: [{ required: true, message: '所属项目不能为空', trigger: 'change' }],
  name: [{ required: true, message: '执行名称不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '执行类型不能为空', trigger: 'change' }],
  pri: [{ required: true, message: '优先级不能为空', trigger: 'change' }]
})

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = type === 'create' ? '新增执行' : '编辑执行'
  formType.value = type
  resetForm()
  // 只允许挂到真实项目下（后端也会校验 type=project）
  projectList.value = await ProjectApi.getProjectSimpleList()
  if (id) {
    formLoading.value = true
    try {
      formData.value = await ExecutionApi.getExecution(id)
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await ExecutionApi.createExecution(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await ExecutionApi.updateExecution(formData.value)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const resetForm = () => {
  formData.value = {
    project: undefined,
    name: '',
    type: 'sprint',
    pri: 3,
    estimate: 0,
    PM: 'admin',
    team: 'admin',
    desc: ''
  }
  formRef.value?.resetFields()
}
</script>
